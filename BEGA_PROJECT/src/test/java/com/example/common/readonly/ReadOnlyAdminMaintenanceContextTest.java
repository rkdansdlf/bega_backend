package com.example.common.readonly;

import com.example.admin.controller.AdminMaintenanceController;
import com.example.cheerboard.scheduler.CheerStorageScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ReadOnlyAdminMaintenanceContextTest {

    @Test
    void readonlyControllerCanStartWithoutCleanupSchedulerAndRejectsManualCleanup() {
        new ApplicationContextRunner()
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("dev", ReadOnlyVerificationPolicy.PROFILE))
                .withUserConfiguration(AdminMaintenanceController.class, CheerStorageScheduler.class,
                        ReadOnlyVerificationPolicy.class)
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(AdminMaintenanceController.class);
                    assertThat(context).doesNotHaveBean(CheerStorageScheduler.class);
                    assertThatThrownBy(() -> context.getBean(AdminMaintenanceController.class)
                            .cleanupSoftDeletedCheerPosts())
                            .isInstanceOf(ReadOnlyVerificationUnavailableException.class);
                });
    }

    @Test
    void normalModeKeepsExplicitManualCleanupDelegation() {
        CheerStorageScheduler scheduler = mock(CheerStorageScheduler.class);
        new ApplicationContextRunner()
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("dev"))
                .withUserConfiguration(AdminMaintenanceController.class, ReadOnlyVerificationPolicy.class)
                .withBean(CheerStorageScheduler.class, () -> scheduler)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var response = context.getBean(AdminMaintenanceController.class).cleanupSoftDeletedCheerPosts();
                    assertThat(response.getStatusCode().value()).isEqualTo(200);
                    assertThat(response.getBody().isSuccess()).isTrue();
                    verify(scheduler).cleanupDeletedPosts();
                });
    }
}
