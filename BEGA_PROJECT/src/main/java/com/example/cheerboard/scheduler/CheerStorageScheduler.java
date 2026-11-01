package com.example.cheerboard.scheduler;

import com.example.common.readonly.ReadOnlyVerificationPolicy;
import org.springframework.context.annotation.Profile;

import com.example.cheerboard.repo.CheerPostRepo;
import com.example.cheerboard.service.CheerPostService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.scheduling.JobScheduler;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Component
@Profile("!" + ReadOnlyVerificationPolicy.PROFILE)
@RequiredArgsConstructor
@Slf4j
public class CheerStorageScheduler implements ApplicationRunner {

    private static final int MAX_POSTS_PER_QUEUE = 50;

    private final CheerPostRepo postRepo;
    private final CheerPostService postService;
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
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void cleanupDeletedPosts() {
        // Soft Deleted 상태인 게시글 조회 (Native Query)
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        List<Long> deletedPosts = new java.util.ArrayList<>(postRepo.findDuePurgeIds(
                now, 0, PageRequest.of(0, MAX_POSTS_PER_QUEUE)));
        deletedPosts.addAll(postRepo.findDuePurgeIds(now, 1, PageRequest.of(0, MAX_POSTS_PER_QUEUE)));

        if (deletedPosts.isEmpty()) {
            return;
        }

        log.info("Soft Deleted 게시글 정리 시작: 대상 {}개", deletedPosts.size());

        for (Long postId : deletedPosts) {
            try {
                if (postService.cleanupSoftDeletedPost(postId)) {
                    log.info("게시글 영구 삭제 완료: postId={}", postId);
                } else {
                    log.warn("게시글 정리 미완료로 영구 삭제 보류: postId={}", postId);
                }
            } catch (Exception e) {
                log.error("게시글 정리 중 오류 발생: postId={}, error={}", postId, e.getMessage());
            }
        }
    }
}
