package com.example.cheerboard.storage.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import static com.example.common.config.CacheConfig.POST_IMAGE_URLS;
import static com.example.common.config.CacheConfig.SIGNED_URLS;

import org.springframework.cache.CacheManager;

import com.example.cheerboard.config.CurrentUser;
import com.example.cheerboard.domain.CheerPost;
import com.example.cheerboard.repo.CheerPostRepo;
import com.example.cheerboard.service.PermissionValidator;
import com.example.cheerboard.storage.client.SupabaseStorageClient;
import com.example.cheerboard.storage.config.StorageConfig;
import com.example.cheerboard.storage.dto.PostImageDto;
import com.example.cheerboard.storage.dto.SignedUrlDto;
import com.example.cheerboard.storage.entity.PostImage;
import com.example.cheerboard.storage.repository.PostImageRepository;
import com.example.cheerboard.storage.validator.ImageValidator;
import com.example.auth.entity.UserEntity;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * 이미지 업로드/삭제/조회 서비스
 * - 보상 트랜잭션: 스토리지 업로드 성공 -> DB 실패 시 스토리지 롤백
 * - 썸네일 단일성: 게시글당 1개만 허용
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ImageService {

    private final PostImageRepository postImageRepo;
    private final CheerPostRepo postRepo;
    private final SupabaseStorageClient storageClient;
    private final ImageValidator validator;
    private final StorageConfig config;
    private final CurrentUser currentUser;
    private final PermissionValidator permissionValidator;
    private final CacheManager cacheManager;
    private final ImageCompressor imageCompressor;

    /**
     * 게시글 이미지 업로드 (여러 파일)
     */
    @CacheEvict(value = POST_IMAGE_URLS, key = "#postId")
    @Transactional
    public List<PostImageDto> uploadPostImages(Long postId, List<MultipartFile> files) {
        log.info("이미지 업로드 시작: postId={}, 파일 수={}", postId, files.size());
        UserEntity me = currentUser.get();
        CheerPost post = findPostById(postId);

        // 권한 체크: 작성자 또는 관리자만
        permissionValidator.validateOwnerOrAdmin(me, post.getAuthor(), "이미지 업로드");

        // 현재 이미지 개수 확인
        // Null type safety 해결: Primitive long으로 변환하여 전달
        long currentCount = postImageRepo.countByPostId(Objects.requireNonNull(postId).longValue());
        log.debug("현재 저장된 이미지 수: {}", currentCount);
        validator.validateFiles(files, (int) currentCount);

        List<PostImageDto> uploadedImages = new ArrayList<>();
        List<String> uploadedPaths = new ArrayList<>(); // 보상 삭제용

        for (int i = 0; i < files.size(); i++) {
            MultipartFile file = files.get(i);
            log.info("파일 업로드 중 ({}/{}): name={}, size={} bytes, type={}",
                    i + 1, files.size(), file.getOriginalFilename(), file.getSize(), file.getContentType());

            try {
                // 1. 서버 사이드 이미지 압축
                byte[] compressedBytes = imageCompressor.compress(file);
                long compressedSize = compressedBytes.length;
                log.debug("이미지 압축 완료: 원본={}bytes, 압축 후={}bytes", file.getSize(), compressedSize);

                String storagePath = generateStoragePath("posts", postId, file);
                log.debug("스토리지 경로 생성: {}", storagePath);

                // 2. 스토리지 업로드 (압축된 바이트 배열 사용)
                log.debug("Supabase Storage 업로드 시도: path={}", storagePath);
                var uploadResult = storageClient.uploadBytes(
                        compressedBytes,
                        file.getContentType(),
                        config.getCheerBucket(),
                        storagePath).block();
                if (uploadResult == null) {
                    log.error("스토리지 업로드 결과가 null입니다: path={}", storagePath);
                    throw new RuntimeException("스토리지 업로드 실패");
                }
                log.info("Supabase Storage 업로드 성공: path={}", storagePath);
                uploadedPaths.add(storagePath);

                // 3. DB 저장 (압축된 크기 저장)
                PostImage image = PostImage.builder()
                        .post(post)
                        .storagePath(storagePath)
                        .mimeType(file.getContentType())
                        .bytes(compressedSize)
                        .isThumbnail(false)
                        .build();

                // Null type safety 해결: image 객체 null 체크
                postImageRepo.save(Objects.requireNonNull(image));
                log.info("DB 저장 성공: imageId={}, path={}", image.getId(), storagePath);

                uploadedImages.add(new PostImageDto(
                        image.getId(),
                        image.getStoragePath(),
                        image.getMimeType(),
                        image.getBytes(),
                        image.getIsThumbnail(),
                        generateSignedUrl(image.getStoragePath())));

            } catch (Exception e) {
                log.error("이미지 업로드 실패, 보상 삭제 수행: postId={}, 파일={}, uploadedPaths={}",
                        postId, file.getOriginalFilename(), uploadedPaths, e);
                // 보상 트랜잭션: 이미 업로드된 파일들 삭제
                compensateUploadFailure(uploadedPaths);
                throw new RuntimeException("이미지 업로드 중 오류가 발생했습니다: " + e.getMessage(), e);
            }
        }

        log.info("이미지 업로드 완료: postId={}, 성공 {}개", postId, uploadedImages.size());
        return uploadedImages;
    }

    /**
     * 게시글 이미지 목록 조회
     */
    @Transactional(readOnly = true)
    public List<PostImageDto> listPostImages(Long postId) {
        List<PostImage> images = postImageRepo.findByPostIdOrderByCreatedAtAsc(postId);

        return images.stream()
                .map(image -> new PostImageDto(
                        image.getId(),
                        image.getStoragePath(),
                        image.getMimeType(),
                        image.getBytes(),
                        image.getIsThumbnail(),
                        generateSignedUrl(image.getStoragePath())))
                .toList();
    }

    /**
     * 게시글의 이미지 object key 목록 조회 (DTO 변환용)
     */
    @Transactional(readOnly = true)
    public List<String> getPostImageKeys(Long postId) {
        log.debug("이미지 경로 조회 시작: postId={}", postId);
        List<PostImage> images = postImageRepo.findByPostIdOrderByCreatedAtAsc(postId);
        log.debug("DB에서 조회된 이미지 수: {}", images.size());

        List<String> keys = images.stream()
                .map(PostImage::getStoragePath)
                .filter(path -> path != null && !path.isEmpty())
                .toList();

        log.info("이미지 경로 조회 완료: postId={}, 총 {}개", postId, keys.size());
        return keys;
    }

    /**
     * 게시글의 이미지 서명 URL 목록 조회 (DTO 변환용)
     * 캐싱: postId 기준으로 5분간 캐시 (signed URL TTL보다 짧게 설정)
     */
    @Cacheable(value = POST_IMAGE_URLS, key = "#postId")
    @Transactional(readOnly = true)
    public List<String> getPostImageUrls(Long postId) {
        log.debug("이미지 URL 조회 시작: postId={}", postId);
        List<PostImage> images = postImageRepo.findByPostIdOrderByCreatedAtAsc(postId);
        log.debug("DB에서 조회된 이미지 수: {}", images.size());

        List<String> urls = images.stream()
                .map(image -> {
                    String url = generateSignedUrl(image.getStoragePath());
                    log.debug("이미지 URL 생성: path={}, url={}", image.getStoragePath(), url != null ? "성공" : "실패");
                    return url;
                })
                .filter(url -> url != null && !url.isEmpty())
                .toList();

        log.info("이미지 URL 조회 완료: postId={}, 총 {}개", postId, urls.size());
        return urls;
    }

    /**
     * 여러 게시글의 이미지 URL 목록 조회 (목록 페이지용)
     * - 캐시에 존재하는 경우 재사용
     * - 없는 경우에만 일괄 조회 후 캐싱
     */
    @Transactional(readOnly = true)
    public Map<Long, List<String>> getPostImageUrlsByPostIds(List<Long> postIds) {
        Map<Long, List<String>> result = new HashMap<>();
        if (postIds == null || postIds.isEmpty()) {
            return result;
        }

        var cache = cacheManager.getCache(POST_IMAGE_URLS);
        List<Long> missingPostIds = new ArrayList<>();

        if (cache != null) {
            for (Long postId : postIds) {
                if (postId == null)
                    continue;
                @SuppressWarnings("unchecked")
                List<String> cached = cache.get(postId, List.class);
                if (cached != null) {
                    result.put(postId, cached);
                } else {
                    missingPostIds.add(postId);
                }
            }
        } else {
            missingPostIds.addAll(postIds);
        }

        if (missingPostIds.isEmpty()) {
            return result;
        }

        List<PostImage> images = postImageRepo.findByPostIdInOrderByPostIdAscCreatedAtAsc(missingPostIds);
        Map<Long, List<String>> groupedUrls = new HashMap<>();

        for (PostImage image : images) {
            Long postId = image.getPost().getId();
            String url = generateSignedUrl(image.getStoragePath());
            if (url == null || url.isEmpty()) {
                continue;
            }
            groupedUrls.computeIfAbsent(postId, key -> new ArrayList<>()).add(url);
        }

        for (Long postId : missingPostIds) {
            List<String> urls = groupedUrls.getOrDefault(postId, Collections.emptyList());
            result.put(postId, urls);
            if (cache != null && postId != null) {
                cache.put(postId, urls);
            }
        }

        return result;
    }

    /**
     * 이미지 삭제
     */
    @Transactional
    public void deleteImage(Long imageId) {
        UserEntity me = currentUser.get();
        PostImage image = postImageRepo.findById(Objects.requireNonNull(imageId))
                .orElseThrow(() -> new java.util.NoSuchElementException("이미지를 찾을 수 없습니다: " + imageId));

        // 권한 체크
        permissionValidator.validateOwnerOrAdmin(me, image.getPost().getAuthor(), "이미지 삭제");

        // 캐시 무효화를 위해 postId 저장
        Long postId = image.getPost().getId();

        // 1. DB 삭제
        postImageRepo.delete(image);

        // 2. 스토리지 삭제
        try {
            storageClient.delete(config.getCheerBucket(), image.getStoragePath()).block();
        } catch (Exception e) {
            log.error("스토리지 삭제 실패 (DB는 이미 삭제됨): path={}", image.getStoragePath(), e);
            // DB는 이미 삭제되었으므로 스토리지 삭제 실패는 로그만 남김
        }

        // 3. 이미지 URL 캐시 무효화
        evictPostImageCache(postId);
        evictSignedUrlCache(image.getStoragePath());
    }

    /**
     * 게시글 이미지 URL 캐시 무효화
     */
    private void evictPostImageCache(Long postId) {
        var cache = cacheManager.getCache(POST_IMAGE_URLS);
        if (cache != null) {
            cache.evict(Objects.requireNonNull(postId));
            log.debug("이미지 URL 캐시 무효화: postId={}", postId);
        }
    }

    /**
     * 개별 Signed URL 캐시 무효화
     */
    private void evictSignedUrlCache(String storagePath) {
        var cache = cacheManager.getCache(SIGNED_URLS);
        if (cache != null && storagePath != null) {
            cache.evict(storagePath);
            log.debug("Signed URL 캐시 무효화: path={}", storagePath);
        }
    }

    /**
     * 서명 URL 갱신
     */

    public SignedUrlDto renewSignedUrl(Long imageId) {
        PostImage image = postImageRepo.findById(Objects.requireNonNull(imageId))
                .orElseThrow(() -> new java.util.NoSuchElementException("이미지를 찾을 수 없습니다: " + imageId));

        String signedUrl = generateSignedUrl(image.getStoragePath());
        Instant expiresAt = Instant.now().plusSeconds(config.getSignedUrlTtlSeconds());

        return new SignedUrlDto(signedUrl, expiresAt);
    }

    /**
     * 썸네일로 지정
     * - 기존 썸네일이 있으면 해제하고 새로 지정
     */
    @Transactional

    public PostImageDto markAsThumbnail(Long imageId) {
        UserEntity me = currentUser.get();
        PostImage image = postImageRepo.findById(Objects.requireNonNull(imageId))
                .orElseThrow(() -> new java.util.NoSuchElementException("이미지를 찾을 수 없습니다: " + imageId));

        // 권한 체크
        permissionValidator.validateOwnerOrAdmin(me, image.getPost().getAuthor(), "썸네일 지정");

        // 기존 썸네일 해제
        postImageRepo.findByPostIdAndIsThumbnailTrue(image.getPost().getId())
                .ifPresent(oldThumbnail -> {
                    oldThumbnail.setIsThumbnail(false);
                    postImageRepo.save(oldThumbnail);
                });

        // 새 썸네일 지정
        image.setIsThumbnail(true);
        postImageRepo.save(image);

        return new PostImageDto(
                image.getId(),
                image.getStoragePath(),
                image.getMimeType(),
                image.getBytes(),
                image.getIsThumbnail(),
                generateSignedUrl(image.getStoragePath()));
    }

    /**
     * 스토리지 경로 생성
     * 형식: posts/{postId}/{uuid}.{ext} 또는 comments/{commentId}/{uuid}.{ext}
     */
    private String generateStoragePath(String prefix, Long entityId, MultipartFile file) {
        String extension = validator.getFileExtension(file.getOriginalFilename());
        String uuid = UUID.randomUUID().toString();
        // entityId가 Nullable로 오인되지 않도록 Objects.requireNonNull 및 primitive 변환 사용
        return String.format("%s/%d/%s.%s", prefix, Objects.requireNonNull(entityId).longValue(), uuid, extension);
    }

    /**
     * 서명 URL 생성
     */
    private String generateSignedUrl(String storagePath) {
        var cache = cacheManager.getCache(SIGNED_URLS);
        if (cache != null && storagePath != null) {
            String cached = cache.get(storagePath, String.class);
            if (cached != null && !cached.isBlank()) {
                log.info("Signed URL cache hit: path={}", storagePath);
                return cached;
            }
            log.info("Signed URL cache miss: path={}", storagePath);
        }
        try {
            var response = storageClient.createSignedUrl(config.getCheerBucket(), storagePath,
                    Objects.requireNonNull(config.getSignedUrlTtlSeconds()).intValue()).block();
            String signedUrl = response != null ? response.signedUrl() : null;
            if (cache != null && signedUrl != null && !signedUrl.isBlank() && storagePath != null) {
                cache.put(storagePath, signedUrl);
            }
            return signedUrl;
        } catch (Exception e) {
            log.error("서명 URL 생성 실패: path={}", storagePath, e);
            return null;
        }
    }

    /**
     * 업로드 실패 시 보상 트랜잭션: 이미 업로드된 파일들 삭제
     */
    private void compensateUploadFailure(List<String> uploadedPaths) {
        for (String path : uploadedPaths) {
            try {
                storageClient.delete(config.getCheerBucket(), path).block();
                log.info("보상 삭제 성공: path={}", path);
            } catch (Exception e) {
                log.error("보상 삭제 실패: path={}", path, e);
            }
        }
    }

    /**
     * 게시글 조회
     */

    private CheerPost findPostById(Long postId) {
        return postRepo.findById(Objects.requireNonNull(postId))
                .orElseThrow(() -> new java.util.NoSuchElementException("게시글을 찾을 수 없습니다: " + postId));
    }

    // 다이어리 스토리지

    /**
     * 다이어리 이미지 업로드 (0개, 1개, 여러 개 모두 처리)
     */
    @Transactional
    public Mono<List<String>> uploadDiaryImages(Long userId, Long diaryId, List<MultipartFile> files) {
        log.info("다이어리 이미지 업로드 시작: userId={}, diaryId={}, 파일 수={}",
                userId, diaryId, files != null ? files.size() : 0);

        // 빈 리스트 체크
        if (files == null || files.isEmpty()) {
            log.debug("업로드할 파일이 없습니다.");
            return Mono.just(List.of());
        }

        // 파일 개수 검증
        if (files.size() > Objects.requireNonNull(config.getMaxImagesPerDiary()).intValue()) {
            return Mono.error(new IllegalArgumentException(
                    String.format("이미지는 최대 %d개까지 업로드할 수 있습니다.",
                            Objects.requireNonNull(config.getMaxImagesPerDiary()).intValue())));
        }

        List<Mono<String>> uploadMonos = new ArrayList<>();

        for (MultipartFile file : files) {
            // 각 파일 유효성 검사
            validator.validateFile(file);

            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }

            String fileName = UUID.randomUUID() + extension;
            // userId와 diaryId 명시적 Null 체크 및 primitive 변환
            String storagePath = String.format("diary/%d/%d/%s", Objects.requireNonNull(userId).longValue(),
                    Objects.requireNonNull(diaryId).longValue(), fileName);

            Mono<String> uploadMono = storageClient.upload(file, config.getDiaryBucket(), storagePath)
                    .map(response -> {
                        log.info("다이어리 이미지 업로드 성공: path={}", storagePath);
                        return storagePath;
                    })
                    .doOnError(err -> log.error("다이어리 이미지 업로드 실패: path={}, error={}",
                            storagePath, err.getMessage()));

            uploadMonos.add(uploadMono);
        }

        // 모든 업로드를 병렬로 실행
        return Mono.zip(uploadMonos, results -> {
            List<String> paths = new ArrayList<>();
            for (Object result : results) {
                if (result != null) {
                    paths.add((String) result);
                }
            }
            log.info("다이어리 이미지 업로드 완료: userId={}, diaryId={}, 성공 {}개",
                    userId, diaryId, paths.size());
            return paths;
        }).onErrorResume(err -> {
            log.error("다이어리 이미지 업로드 중 오류 발생: userId={}, diaryId={}, error={}",
                    userId, diaryId, err.getMessage());
            return Mono.error(new RuntimeException("이미지 업로드 중 오류가 발생했습니다: " + err.getMessage()));
        });
    }

    /**
     * 다이어리 이미지 삭제 (0개, 1개, 여러 개 모두 처리)
     */
    @Transactional
    public Mono<Void> deleteDiaryImages(List<String> storagePaths) {
        log.info("다이어리 이미지 삭제 시작: 총 {}개", storagePaths != null ? storagePaths.size() : 0);

        // 빈 리스트 체크
        if (storagePaths == null || storagePaths.isEmpty()) {
            log.debug("삭제할 파일이 없습니다.");
            return Mono.empty();
        }

        List<Mono<Void>> deleteMonos = storagePaths.stream()
                .map(path -> storageClient.delete(config.getDiaryBucket(), path)
                        .doOnSuccess(v -> log.info("다이어리 이미지 삭제 성공: path={}", path))
                        .doOnError(err -> log.error("다이어리 이미지 삭제 실패: path={}, error={}",
                                path, err.getMessage())))
                .toList();

        return Mono.when(deleteMonos)
                .doOnSuccess(v -> log.info("다이어리 이미지 삭제 완료: 총 {}개", storagePaths.size()))
                .doOnError(err -> log.error("다이어리 이미지 삭제 중 오류 발생: error={}", err.getMessage()));
    }

    /**
     * 다이어리 이미지 Signed URL 생성 (0개, 1개, 여러 개 모두 처리)
     */
    public Mono<List<String>> getDiaryImageSignedUrls(List<String> storagePaths) {
        log.debug("다이어리 이미지 URL 생성 시작: 총 {}개", storagePaths != null ? storagePaths.size() : 0);

        // 빈 리스트 체크
        if (storagePaths == null || storagePaths.isEmpty()) {
            log.debug("URL 생성할 파일이 없습니다.");
            return Mono.just(List.of());
        }

        List<Mono<String>> urlMonos = storagePaths.stream()
                // config.getDiaryBucket()이 null일 가능성 차단
                .map(path -> storageClient
                        .createSignedUrl(Objects.requireNonNull(config.getDiaryBucket()), path,
                                Objects.requireNonNull(config.getSignedUrlTtlSeconds()).intValue())
                        .map(response -> response.signedUrl())
                        .doOnError(err -> log.error("다이어리 이미지 URL 생성 실패: path={}, error={}",
                                path, err.getMessage())))
                .toList();

        return Mono.zip(urlMonos, results -> {
            List<String> urls = new ArrayList<>();
            for (Object result : results) {
                if (result != null && !result.toString().isEmpty()) {
                    urls.add((String) result);
                }
            }
            log.info("다이어리 이미지 URL 생성 완료: 총 {}개", urls.size());
            return urls;
        });
    }

}
