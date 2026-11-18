package com.example.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.auth.entity.LegacyPostImageHold;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:legacy_image_hold;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
class LegacyPostImageHoldRepositoryIntegrationTest {
    @Autowired private LegacyPostImageHoldRepository holds;
    @Autowired private EntityManager entityManager;

    @Test
    void storesOriginalValuesAndFindsUnresolvedHoldsWithoutParentRows() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 1, 2, 3, 4, 5);
        holds.saveAndFlush(LegacyPostImageHold.builder()
                .postImageId(12L).ownerUserId(34L).postId(56L)
                .storagePath(" /bucket/legacy%2Fimage.webp ")
                .logicalBucket("synthetic-bucket").reportProtected(true).createdAt(createdAt).build());
        entityManager.clear();

        LegacyPostImageHold hold = holds.findById(12L).orElseThrow();
        assertThat(hold.getOwnerUserId()).isEqualTo(34L);
        assertThat(hold.getPostId()).isEqualTo(56L);
        assertThat(hold.getStoragePath()).isEqualTo(" /bucket/legacy%2Fimage.webp ");
        assertThat(hold.getLogicalBucket()).isEqualTo("synthetic-bucket");
        assertThat(hold.isReportProtected()).isTrue();
        assertThat(hold.getStatus()).isEqualTo(LegacyPostImageHold.Status.UNRESOLVED);
        assertThat(hold.getCreatedAt()).isEqualTo(createdAt);
        assertThat(holds.existsByOwnerUserId(34L)).isTrue();
        assertThat(holds.existsByOwnerUserId(35L)).isFalse();
        assertThat(holds.findByOwnerUserIdOrderByPostImageIdAsc(34L, PageRequest.of(0, 10)))
                .extracting(LegacyPostImageHold::getPostImageId).containsExactly(12L);
    }
}
