package com.example.cheerboard.service;

import com.example.cheerboard.repo.CheerPostRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.scheduling.JobScheduler;
import org.jobrunr.scheduling.cron.Cron;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class PostSyncScheduler implements ApplicationRunner {

    private final RedisPostService redisPostService;
    private final CheerPostRepo postRepo;
    private final CheerService cheerService;
    private final JobScheduler jobScheduler;

    @Override
    public void run(ApplicationArguments args) {
        // 앱 시작 시 Persistent Recurrent Job 등록
        // 5분마다 실행 (Views Sync)
        jobScheduler.scheduleRecurrently("sync-view-counts", Cron.every5minutes(), this::syncViewCounts);

        // 매일 새벽 4시 실행 (Hot Post Prune)
        jobScheduler.scheduleRecurrently("prune-hot-posts", Cron.daily(4, 0), this::pruneHotPosts);
    }

    /**
     * JobRunr에 의해 실행될 백그라운드 작업
     * Job 어노테이션을 통해 대시보드에서 식별 가능
     */
    @Job(name = "Sync Redis View Counts to DB")
    @Transactional
    public void syncViewCounts() {
        Set<Long> dirtyPostIds = redisPostService.getDirtyPostIds();
        if (dirtyPostIds.isEmpty()) {
            return;
        }

        log.info("Starting persistent sync job for {} posts", dirtyPostIds.size());

        for (Long postId : dirtyPostIds) {
            try {
                processPostSync(postId);
            } catch (Exception e) {
                log.error("Failed to sync post {}: {}", postId, e.getMessage());
            }
        }
        log.info("Post sync job completed");
    }

    @Transactional
    public void processPostSync(Long postId) {
        postRepo.findById(Objects.requireNonNull(postId)).ifPresent(post -> {
            Integer viewsInRedis = redisPostService.getViewCount(postId);
            if (viewsInRedis != null && viewsInRedis > 0) {
                postRepo.incrementViewCountByDelta(postId, viewsInRedis);
                redisPostService.clearDirtyPost(postId, viewsInRedis);
                post.setViews(post.getViews() + viewsInRedis);
                cheerService.updateHotScore(post);
            }
        });
    }

    @Job(name = "Prune Hot Post List")
    public void pruneHotPosts() {
        log.info("Pruning HOT post list in Redis...");
        redisPostService.pruneHotList(1000);
    }
}
