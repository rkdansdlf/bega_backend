package com.example.cheerboard.storage.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

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
import com.example.cheerboard.storage.dto.PostImageDto;
import com.example.cheerboard.storage.dto.SignedUrlDto;
import com.example.cheerboard.storage.entity.PostImage;
import com.example.cheerboard.storage.repository.PostImageRepository;
import com.example.cheerboard.storage.strategy.StorageStrategy;
import com.example.cheerboard.storage.validator.ImageValidator;
import com.example.cheerboard.storage.config.StorageConfig;
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
    private final StorageStrategy storageStrategy; // OCI Object Storage Strategy
    private final ImageValidator validator;
    private final StorageConfig config;
    private final CurrentUser currentUser;
    // 리팩토링된 컴포넌트들
    private final PermissionValidator permissionValidator;
    private final CacheManager cacheManager;
    private final com.example.common.image.ImageUtil imageUtil;

    /**
     * 게시글 이미지 업로드 (여러 파일)
     */
    @CacheEvict(value = POST_IMAGE_URLS, key = "#postId")
    @Transactional
    public List<PostImageDto> uploadPostImages(Long postId, List<MultipartFile> files) {
        log.info("이미지 업로드 시작 (Parallel): postId={}, 파일 수={}", postId, files.size());
        UserEntity me = currentUser.get();
        CheerPost post = findPostById(postId);

        // 권한 체크: 작성자 또는 관리자만
        permissionValidator.validateOwnerOrAdmin(me, post.getAuthor(), "이미지 업로드");

        // 현재 이미지 개수 확인
        long currentCount = postImageRepo.countByPostId(Objects.requireNonNull(postId).longValue());
        log.debug("현재 저장된 이미지 수: {}", currentCount);
        validator.validateFiles(files, (int) currentCount);

        List<PostImageDto> uploadedImages = new ArrayList<>();

        // Async Result Helper
        record UploadResult(String path, com.example.common.image.ImageUtil.ProcessedImage info) {
        }

        // 1. Parallel Process & Upload
        List<CompletableFuture<UploadResult>> futures = files.stream()
                .map(file -> CompletableFuture.supplyAsync(() -> {
                    try {
                        // 1. 서버 사이드 이미지 압축 및 WebP 변환
                        var processed = imageUtil.process(file);
                        log.debug("이미지 처리 완료: 원본={}bytes -> 처리후={}bytes ({})",
                                file.getSize(), processed.getSize(), processed.getExtension());

                        // 스토리지 경로 생성
                        String storagePath = generateStoragePath("posts", postId, processed.getExtension());

                        // 2. 스토리지 업로드 (Blocking IO in Async Thread)
                        log.debug("Parallel Upload Start: path={}", storagePath);

                        // StorageStrategy.uploadBytes returns Mono<String> (path)
                        String uploadedPath = storageStrategy.uploadBytes(
                                processed.getBytes(),
                                processed.getContentType(),
                                config.getCheerBucket(),
                                storagePath)
                                .block();

                        if (uploadedPath == null) {
                            throw new RuntimeException("스토리지 업로드 결과가 null입니다.");
                        }

                        log.debug("Parallel Upload Success: path={}", uploadedPath);
                        return new UploadResult(uploadedPath, processed);
                    } catch (Exception e) {
                        throw new RuntimeException("Async Upload Failed: " + file.getOriginalFilename(), e);
                    }
                }))
                .toList();

        // 2. Wait for All & Handle Failures
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } catch (Exception e) {
            log.error("병렬 업로드 중 오류 발생. 보상 삭제 수행.", e);
            // 성공한 파일들만 추출하여 삭제
            List<String> pathsToDelete = futures.stream()
                    .filter(f -> !f.isCompletedExceptionally() && f.getNow(null) != null)
                    .map(f -> f.getNow(null).path())
                    .toList();

            compensateUploadFailure(pathsToDelete);
            throw new RuntimeException("이미지 업로드 중 오류가 발생했습니다.", e);
        }

        // 3. DB Save (Single Batch Transaction)
        List<UploadResult> results = futures.stream().map(CompletableFuture::join).toList();

        for (UploadResult res : results) {
            PostImage image = PostImage.builder()
                    .post(post)
                    .storagePath(res.path())
                    .mimeType(res.info().getContentType())
                    .bytes(res.info().getSize())
                    .isThumbnail(false)
                    .build();

            postImageRepo.save(Objects.requireNonNull(image));
            log.info("DB 저장 성공: imageId={}, path={}", image.getId(), res.path());

            uploadedImages.add(new PostImageDto(
                    image.getId(),
                    image.getStoragePath(),
                    image.getMimeType(),
                    image.getBytes(),
                    image.getIsThumbnail(),
                    generateSignedUrl(image.getStoragePath())));
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
            storageStrategy.delete(config.getCheerBucket(), image.getStoragePath()).block();
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
     * 게시글의 모든 이미지 삭제 (스토리지 삭제 및 캐시 초기화)
     * DB 데이터는 CheerPost 삭제 시 Cascade로 삭제됨을 가정하거나, 필요 시 여기서 삭제
     */
    @Transactional
    public boolean deleteImagesByPostId(Long postId) {
        log.info("게시글 이미지 일괄 삭제 시작: postId={}", postId);
        List<PostImage> images = postImageRepo.findByPostIdOrderByCreatedAtAsc(postId);

        if (images.isEmpty()) {
            log.debug("삭제할 이미지가 없습니다.");
            return true;
        }

        boolean allSuccess = true;

        // 스토리지 삭제
        for (PostImage image : images) {
            try {
                storageStrategy.delete(config.getCheerBucket(), image.getStoragePath()).block();
                // 개별 Signed URL 캐시 무효화
                evictSignedUrlCache(image.getStoragePath());
            } catch (Exception e) {
                log.error("스토리지 이미지 삭제 실패: path={}, error={}", image.getStoragePath(), e.getMessage());
                allSuccess = false;
            }
        }

        // 게시글 이미지 목록 캐시 무효화
        evictPostImageCache(postId);

        return allSuccess;
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
    private String generateStoragePath(String prefix, Long entityId, String extension) {
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
            // Strategy handles fallback logic internally for Supabase
            // Local storage returns public URL directly
            String url = storageStrategy.getUrl(config.getCheerBucket(), storagePath,
                    config.getSignedUrlTtlSeconds()).block();

            if (cache != null && url != null && !url.isBlank() && storagePath != null) {
                cache.put(storagePath, url);
            }
            return url;
        } catch (Exception e) {
            log.error("URL generation failed for path: {}", storagePath, e);
            return null;
        }
    }

    /**
     * 업로드 실패 시 보상 트랜잭션: 이미 업로드된 파일들 삭제
     */
    private void compensateUploadFailure(List<String> uploadedPaths) {
        for (String path : uploadedPaths) {
            try {
                storageStrategy.delete(config.getCheerBucket(), path).block();
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
        log.info("다이어리 이미지 업로드 시작 (Optimized): userId={}, diaryId={}, 파일 수={}",
                userId, diaryId, files != null ? files.size() : 0);

        if (files == null || files.isEmpty()) {
            return Mono.just(List.of());
        }

        if (files.size() > config.getMaxImagesPerDiary()) {
            return Mono.error(new IllegalArgumentException(
                    String.format("이미지는 최대 %d개까지 업로드할 수 있습니다.",
                            config.getMaxImagesPerDiary())));
        }

        // 1. Process and Upload in Parallel using CompletableFuture (matching
        // uploadPostImages style for consistency)
        return Mono.fromCallable(() -> {
            List<CompletableFuture<String>> futures = files.stream()
                    .map(file -> CompletableFuture.supplyAsync(() -> {
                        try {
                            // 이미지 압축 및 WebP 변환
                            var processed = imageUtil.process(file);

                            String fileName = UUID.randomUUID() + "." + processed.getExtension();
                            String storagePath = String.format("diary/%d/%d/%s",
                                    Objects.requireNonNull(userId).longValue(),
                                    Objects.requireNonNull(diaryId).longValue(),
                                    fileName);

                            log.debug("다이어리 이미지 처리 완료: {}, {}bytes", storagePath, processed.getSize());

                            // 스토리지 업로드
                            String uploadedPath = storageStrategy.uploadBytes(
                                    processed.getBytes(),
                                    processed.getContentType(),
                                    config.getDiaryBucket(),
                                    storagePath)
                                    .block();

                            return uploadedPath;
                        } catch (Exception e) {
                            throw new RuntimeException("다이어리 이미지 업로드 실패: " + file.getOriginalFilename(), e);
                        }
                    }))
                    .toList();

            try {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                return futures.stream()
                        .map(CompletableFuture::join)
                        .filter(Objects::nonNull)
                        .toList();
            } catch (Exception e) {
                log.error("다이어리 병렬 업로드 중 오류 발생", e);
                throw new RuntimeException("이미지 업로드 중 오류가 발생했습니다.", e);
            }
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
                .map(path -> storageStrategy.delete(config.getDiaryBucket(), path)
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
                .map(path -> storageStrategy
                        .getUrl(Objects.requireNonNull(config.getDiaryBucket()), path,
                                config.getSignedUrlTtlSeconds())
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
