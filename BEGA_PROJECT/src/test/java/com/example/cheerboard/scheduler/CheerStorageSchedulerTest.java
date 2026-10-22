package com.example.cheerboard.scheduler;

import com.example.cheerboard.domain.CheerPost;
import com.example.cheerboard.repo.CheerPostRepo;
import com.example.cheerboard.service.CheerPostService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CheerStorageSchedulerTest {

    @Mock private CheerPostRepo postRepo;
    @Mock private CheerPostService postService;
    @Mock private org.jobrunr.scheduling.JobScheduler jobScheduler;

    @InjectMocks
    private CheerStorageScheduler scheduler;

    @Test
    void cleanupDeletedPosts_keepsPostAndMediaLinkWhenStorageDeleteFails() {
        CheerPost post = CheerPost.builder().id(41L).deleted(true).build();
        when(postRepo.findSoftDeletedPosts()).thenReturn(List.of(post));
        when(postService.cleanupSoftDeletedPost(41L)).thenReturn(false);

        scheduler.cleanupDeletedPosts();

        verify(postService).cleanupSoftDeletedPost(41L);
    }

    @Test
    void cleanupDeletedPosts_unlinksMediaOnlyAfterStorageDeleteSucceeds() {
        CheerPost post = CheerPost.builder().id(42L).deleted(true).build();
        when(postRepo.findSoftDeletedPosts()).thenReturn(List.of(post));
        when(postService.cleanupSoftDeletedPost(42L)).thenReturn(true);

        scheduler.cleanupDeletedPosts();

        verify(postService).cleanupSoftDeletedPost(42L);
    }

    @Test
    void cleanupDeletedPosts_retriesSoftDeletedPostOnNextSweep() {
        CheerPost post = CheerPost.builder().id(43L).deleted(true).build();
        when(postRepo.findSoftDeletedPosts()).thenReturn(List.of(post));
        when(postService.cleanupSoftDeletedPost(43L)).thenReturn(false, true);

        scheduler.cleanupDeletedPosts();
        scheduler.cleanupDeletedPosts();

        verify(postService, org.mockito.Mockito.times(2)).cleanupSoftDeletedPost(43L);
    }
}
