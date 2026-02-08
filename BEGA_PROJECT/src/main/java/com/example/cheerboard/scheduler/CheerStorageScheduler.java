package com.example.cheerboard.scheduler;

import com.example.cheerboard.domain.CheerPost;
import com.example.cheerboard.repo.CheerPostRepo;
import com.example.cheerboard.storage.service.ImageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.scheduling.JobScheduler;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class CheerStorageScheduler implements ApplicationRunner {

    private final CheerPostRepo postRepo;
    private final ImageService imageService;
    private final JobScheduler jobScheduler;

    @Override
    public void run(ApplicationArguments args) {
        // 10분마다 실행 (Storage & DB Cleanup)
        jobScheduler.scheduleRecurrently("cleanup-deleted-posts", "*/10 * * * *", this::cleanupDeletedPosts);
    }

    /**
     * Soft Deleted 상태인 게시글을 찾아 스토리지 이미지 삭제 후 DB에서 영구 삭제
     * Cron: 10분마다 실행
     */
    @Job(name = "Cleanup Soft Deleted Posts")
    @Transactional
    public void cleanupDeletedPosts() {
        // Soft Deleted 상태인 게시글 조회 (Native Query)
        List<CheerPost> deletedPosts = postRepo.findSoftDeletedPosts();

        if (deletedPosts.isEmpty()) {
            return;
        }

        log.info("Soft Deleted 게시글 정리 시작: 대상 {}개", deletedPosts.size());

        for (CheerPost post : deletedPosts) {
            try {
                // 스토리지 이미지 삭제 시도
                boolean allImagesDeleted = imageService.deleteImagesByPostId(post.getId());

                if (allImagesDeleted) {
                    // 이미지 삭제 성공 시 DB 영구 삭제 (Native Query)
                    postRepo.hardDeleteById(post.getId());
                    log.info("게시글 영구 삭제 완료: postId={}", post.getId());
                } else {
                    log.warn("이미지 삭제 실패로 영구 삭제 보류: postId={}", post.getId());
                }
            } catch (Exception e) {
                log.error("게시글 정리 중 오류 발생: postId={}, error={}", post.getId(), e.getMessage());
            }
        }
    }
}
