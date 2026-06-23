package com.example.cheerboard.service;

import com.example.cheerboard.domain.CheerPost;
import com.example.cheerboard.repo.CheerPostHotScoreProjection;
import com.example.cheerboard.repo.CheerPostRepo;
import lombok.extern.slf4j.Slf4j;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.scheduling.JobScheduler;
import org.jobrunr.scheduling.cron.Cron;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Set;

@Slf4j
@Component
public class PostSyncScheduler implements ApplicationRunner {

    private final RedisPostService redisPostService;
    private final CheerPostRepo postRepo;
    private final CheerService cheerService;
    private final JobScheduler jobScheduler;
    private final boolean schedulerEnabled;

    public PostSyncScheduler(
            RedisPostService redisPostService,
            CheerPostRepo postRepo,
            CheerService cheerService,
            JobScheduler jobScheduler,
            @Value("${app.cheer.post-sync.scheduler.enabled:true}") boolean schedulerEnabled) {
        this.redisPostService = redisPostService;
        this.postRepo = postRepo;
        this.cheerService = cheerService;
        this.jobScheduler = jobScheduler;
        this.schedulerEnabled = schedulerEnabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!schedulerEnabled) {
            log.info("Skipping post sync recurrent jobs because app.cheer.post-sync.scheduler.enabled=false");
            return;
        }

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
        Long requiredPostId = Objects.requireNonNull(postId);
        Integer viewsInRedis = redisPostService.getViewCount(requiredPostId);
        if (viewsInRedis == null || viewsInRedis <= 0) {
            return;
        }

        postRepo.findHotScoreProjectionById(requiredPostId).ifPresent(post -> {
            postRepo.incrementViewCountByDelta(requiredPostId, viewsInRedis);
            redisPostService.clearDirtyPost(requiredPostId, viewsInRedis);
            cheerService.updateHotScore(toHotScorePost(post, viewsInRedis));
        });
    }

    private CheerPost toHotScorePost(CheerPostHotScoreProjection post, int viewDelta) {
        return CheerPost.builder()
                .id(post.getId())
                .views(post.getViews() + viewDelta)
                .likeCount(post.getLikeCount())
                .commentCount(post.getCommentCount())
                .repostCount(post.getRepostCount())
                .createdAt(post.getCreatedAt())
                .build();
    }

    @Job(name = "Prune Hot Post List")
    public void pruneHotPosts() {
        log.info("Pruning HOT post list in Redis...");
        redisPostService.pruneHotList(1000);
    }
}
