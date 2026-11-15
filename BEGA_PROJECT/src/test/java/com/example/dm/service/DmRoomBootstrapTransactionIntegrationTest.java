package com.example.dm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserFollowRepository;
import com.example.auth.repository.UserRepository;
import com.example.dm.dto.DmRoomDto;
import com.example.dm.repository.DmRoomRepository;
import com.example.profile.storage.service.ProfileImageService;

@DataJpaTest
@Import(DmRoomService.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:dm_room_bootstrap_race;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "spring.jpa.properties.hibernate.show_sql=false"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DmRoomBootstrapTransactionIntegrationTest {

    private static final long TIMEOUT_SECONDS = 10;

    @Autowired
    private DmRoomService dmRoomService;

    @Autowired
    private DmRoomRepository dmRoomRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private UserFollowRepository userFollowRepository;

    @MockitoBean
    private ProfileImageService profileImageService;

    private TransactionTemplate transactionTemplate;
    private Long ownerId;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        ownerId = transactionTemplate.execute(status -> userRepository
                .saveAndFlush(user("@owner", "owner@example.invalid", null))
                .getId());
        transactionTemplate.execute(status -> userRepository
                .saveAndFlush(user("@target", "target@example.invalid", "profiles/target.png")));
    }

    @Test
    @DisplayName("a competing room bootstrap resolves the room created by the winning request")
    void bootstrapRoom_recoversAfterConcurrentUniqueInsert() throws Exception {
        CountDownLatch bothRequestsCheckedForExistingRoom = new CountDownLatch(2);
        doAnswer(invocation -> {
            bothRequestsCheckedForExistingRoom.countDown();
            await(bothRequestsCheckedForExistingRoom);
            return true;
        }).when(userFollowRepository).existsById(any());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<DmRoomDto.BootstrapResponse> first = executor.submit(this::bootstrapRoom);
            Future<DmRoomDto.BootstrapResponse> second = executor.submit(this::bootstrapRoom);

            assertThat(bothRequestsCheckedForExistingRoom.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();

            int successfulResponses = 0;
            List<Throwable> requestFailures = new ArrayList<>();
            for (Future<DmRoomDto.BootstrapResponse> request : List.of(first, second)) {
                try {
                    DmRoomDto.BootstrapResponse response = request.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    assertThat(response.getRoomId()).isNotNull();
                    successfulResponses += 1;
                } catch (ExecutionException exception) {
                    requestFailures.add(exception.getCause());
                }
            }

            assertThat(requestFailures)
                    .as("the losing request should not fail while resolving the winning insert")
                    .isEmpty();
            assertThat(successfulResponses)
                    .as("both requests should resolve the one room created by the winning transaction")
                    .isEqualTo(2);
        } finally {
            bothRequestsCheckedForExistingRoom.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(dmRoomRepository.findAllByParticipantId(ownerId)).hasSize(1);
    }

    private DmRoomDto.BootstrapResponse bootstrapRoom() {
        return dmRoomService.bootstrapRoom(
                ownerId,
                DmRoomDto.BootstrapRequest.builder().targetHandle("@target").build());
    }

    private UserEntity user(String handle, String email, String profileImageUrl) {
        return UserEntity.builder()
                .uniqueId(UUID.randomUUID())
                .handle(handle)
                .name(handle)
                .email(email)
                .profileImageUrl(profileImageUrl)
                .role("ROLE_USER")
                .build();
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for the winning room transaction");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for room bootstrap", exception);
        }
    }
}
