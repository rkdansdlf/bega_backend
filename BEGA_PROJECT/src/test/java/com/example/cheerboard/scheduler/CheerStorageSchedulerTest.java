package com.example.cheerboard.scheduler;

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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class CheerStorageSchedulerTest {

    @Mock private CheerPostRepo postRepo;
    @Mock private CheerPostService postService;
    @Mock private org.jobrunr.scheduling.JobScheduler jobScheduler;

    @InjectMocks
    private CheerStorageScheduler scheduler;

    @Test
    void cleanupDeletedPosts_keepsPostAndMediaLinkWhenStorageDeleteFails() {
        when(postRepo.findDuePurgeIds(any(), eq(0), any(Pageable.class))).thenReturn(List.of(41L));
        when(postService.cleanupSoftDeletedPost(41L)).thenReturn(false);

        scheduler.cleanupDeletedPosts();

        verify(postService).cleanupSoftDeletedPost(41L);
    }

    @Test
    void cleanupDeletedPosts_unlinksMediaOnlyAfterStorageDeleteSucceeds() {
        when(postRepo.findDuePurgeIds(any(), eq(0), any(Pageable.class))).thenReturn(List.of(42L));
        when(postService.cleanupSoftDeletedPost(42L)).thenReturn(true);

        scheduler.cleanupDeletedPosts();

        verify(postService).cleanupSoftDeletedPost(42L);
    }

    @Test
    void cleanupDeletedPosts_retriesSoftDeletedPostOnNextSweep() {
        when(postRepo.findDuePurgeIds(any(), eq(0), any(Pageable.class))).thenReturn(List.of(43L));
        when(postService.cleanupSoftDeletedPost(43L)).thenReturn(false, true);

        scheduler.cleanupDeletedPosts();
        scheduler.cleanupDeletedPosts();

        verify(postService, org.mockito.Mockito.times(2)).cleanupSoftDeletedPost(43L);
    }

    @Test
    void cleanupDeletedPosts_requestsAtMostOneHundredIds() {
        scheduler.cleanupDeletedPosts();
        var page = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(postRepo).findDuePurgeIds(any(), eq(0), page.capture());
        verify(postRepo).findDuePurgeIds(any(), eq(1), page.capture());
        org.junit.jupiter.api.Assertions.assertEquals(List.of(50, 50),
                page.getAllValues().stream().map(Pageable::getPageSize).toList());
    }

    @Test
    void cleanupDeletedPosts_processesNewAndRetryQueuesIndependently() {
        when(postRepo.findDuePurgeIds(any(), eq(0), any(Pageable.class))).thenReturn(List.of(10L));
        when(postRepo.findDuePurgeIds(any(), eq(1), any(Pageable.class))).thenReturn(List.of(20L));
        when(postService.cleanupSoftDeletedPost(10L)).thenThrow(new IllegalStateException("synthetic failure"));
        when(postService.cleanupSoftDeletedPost(20L)).thenReturn(true);
        scheduler.cleanupDeletedPosts();
        verify(postService).cleanupSoftDeletedPost(10L);
        verify(postService).cleanupSoftDeletedPost(20L);
    }
}
