package com.example.cheerboard.storage.service;

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
import com.example.demo.entity.UserEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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

    /**
     * 게시글 이미지 업로드 (여러 파일)
     */
    @Transactional
    public List<PostImageDto> uploadPostImages(Long postId, List<MultipartFile> files) {
        log.info("이미지 업로드 시작: postId={}, 파일 수={}", postId, files.size());
        UserEntity me = currentUser.get();
        CheerPost post = findPostById(postId);

        // 권한 체크: 작성자 또는 관리자만
        permissionValidator.validateOwnerOrAdmin(me, post.getAuthor(), "이미지 업로드");

        // 현재 이미지 개수 확인
        long currentCount = postImageRepo.countByPostId(postId);
        log.debug("현재 저장된 이미지 수: {}", currentCount);
        validator.validateFiles(files, (int) currentCount);

        List<PostImageDto> uploadedImages = new ArrayList<>();
        List<String> uploadedPaths = new ArrayList<>(); // 보상 삭제용

        for (int i = 0; i < files.size(); i++) {
            MultipartFile file = files.get(i);
            log.info("파일 업로드 중 ({}/{}): name={}, size={} bytes, type={}",
                i + 1, files.size(), file.getOriginalFilename(), file.getSize(), file.getContentType());

            try {
                String storagePath = generateStoragePath("posts", postId, file);
                log.debug("스토리지 경로 생성: {}", storagePath);

                // 1. 스토리지 업로드
                log.debug("Supabase Storage 업로드 시도: path={}", storagePath);
                var uploadResult = storageClient.upload(file, storagePath).block();
                if (uploadResult == null) {
                    log.error("스토리지 업로드 결과가 null입니다: path={}", storagePath);
                    throw new RuntimeException("스토리지 업로드 실패");
                }
                log.info("Supabase Storage 업로드 성공: path={}", storagePath);
                uploadedPaths.add(storagePath);

                // 2. DB 저장
                PostImage image = PostImage.builder()
                    .post(post)
                    .storagePath(storagePath)
                    .mimeType(file.getContentType())
                    .bytes(file.getSize())
                    .isThumbnail(false)
                    .build();

                postImageRepo.save(image);
                log.info("DB 저장 성공: imageId={}, path={}", image.getId(), storagePath);

                uploadedImages.add(new PostImageDto(
                    image.getId(),
                    image.getStoragePath(),
                    image.getMimeType(),
                    image.getBytes(),
                    image.getIsThumbnail()
                ));

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
                image.getIsThumbnail()
            ))
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
     */
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
     * 이미지 삭제
     */
    @Transactional
    public void deleteImage(Long imageId) {
        UserEntity me = currentUser.get();
        PostImage image = postImageRepo.findById(imageId)
            .orElseThrow(() -> new java.util.NoSuchElementException("이미지를 찾을 수 없습니다: " + imageId));

        // 권한 체크
        permissionValidator.validateOwnerOrAdmin(me, image.getPost().getAuthor(), "이미지 삭제");

        // 1. DB 삭제
        postImageRepo.delete(image);

        // 2. 스토리지 삭제
        try {
            storageClient.delete(image.getStoragePath()).block();
        } catch (Exception e) {
            log.error("스토리지 삭제 실패 (DB는 이미 삭제됨): path={}", image.getStoragePath(), e);
            // DB는 이미 삭제되었으므로 스토리지 삭제 실패는 로그만 남김
        }
    }

    /**
     * 서명 URL 갱신
     */
    public SignedUrlDto renewSignedUrl(Long imageId) {
        PostImage image = postImageRepo.findById(imageId)
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
        PostImage image = postImageRepo.findById(imageId)
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
            image.getIsThumbnail()
        );
    }

    /**
     * 스토리지 경로 생성
     * 형식: posts/{postId}/{uuid}.{ext} 또는 comments/{commentId}/{uuid}.{ext}
     */
    private String generateStoragePath(String prefix, Long entityId, MultipartFile file) {
        String extension = validator.getFileExtension(file.getOriginalFilename());
        String uuid = UUID.randomUUID().toString();
        return String.format("%s/%d/%s.%s", prefix, entityId, uuid, extension);
    }

    /**
     * 서명 URL 생성
     */
    private String generateSignedUrl(String storagePath) {
        try {
            var response = storageClient.createSignedUrl(storagePath, config.getSignedUrlTtlSeconds()).block();
            return response != null ? response.signedUrl() : null;
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
                storageClient.delete(path).block();
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
        return postRepo.findById(postId)
            .orElseThrow(() -> new java.util.NoSuchElementException("게시글을 찾을 수 없습니다: " + postId));
    }
}
