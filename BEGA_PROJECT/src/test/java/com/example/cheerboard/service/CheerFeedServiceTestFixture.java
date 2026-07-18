package com.example.cheerboard.service;

import com.example.auth.entity.UserEntity;
import com.example.auth.service.BlockService;
import com.example.auth.service.FollowService;
import com.example.auth.service.PublicVisibilityVerifier;
import com.example.auth.service.UserService;
import com.example.cheerboard.domain.CheerPost;
import com.example.cheerboard.domain.PostType;
import com.example.cheerboard.dto.PostChangesResponse;
import com.example.cheerboard.dto.PostLightweightSummaryRes;
import com.example.cheerboard.dto.PostSummaryRes;
import com.example.cheerboard.repo.CheerBookmarkRepo;
import com.example.cheerboard.repo.CheerPostRepo;
import com.example.cheerboard.storage.service.ImageService;
import com.example.profile.storage.service.ProfileImageService;
import com.example.cheerboard.service.CheerFeedServiceTestSupport.CountingDirectExecutorService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static com.example.cheerboard.service.CheerFeedServiceTestSupport.awaitActiveFeedEnrichmentCount;
import static com.example.cheerboard.service.CheerFeedServiceTestSupport.createRepostPost;
import static com.example.cheerboard.service.CheerFeedServiceTestSupport.createSimplePost;
import static com.example.cheerboard.service.CheerFeedServiceTestSupport.invokeEnrichmentAsync;


@ExtendWith(MockitoExtension.class)
@ExtendWith(MockitoExtension.class)
abstract class CheerFeedServiceTestFixture {

    @InjectMocks
    protected CheerFeedService feedService;

    @Mock
    protected CheerPostRepo postRepo;
    @Mock
    protected RedisPostService redisPostService;
    @Mock
    protected ImageService imageService;
    @Mock
    protected CheerBookmarkRepo bookmarkRepo;
    @Mock
    protected CheerInteractionService interactionService; // FeedService uses InteractionService
    @Mock
    protected PostDtoMapper postDtoMapper;
    @Mock
    protected ProfileImageService profileImageService;
    @Mock
    protected PublicVisibilityVerifier publicVisibilityVerifier;
    @Mock
    protected UserService userService;
    @Mock
    protected FollowService followService;
    @Mock
    protected BlockService blockService;
    @Mock
    protected PermissionValidator permissionValidator;
    @Mock
    protected PopularFeedScoringService popularFeedScoringService;
    @Mock
    protected CheerMonitoringMetricsService metricsService;

    @BeforeEach
    void setUp() {
        lenient().when(publicVisibilityVerifier.canAccess(any(), any())).thenReturn(true);
        lenient().when(popularFeedScoringService.isHotEligible(any(CheerPost.class), anyInt(), any())).thenReturn(true);
    }

}
