package com.example.mate.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.mate.entity.CheckInRecord;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:checkin_history_page;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
class CheckInRecordPaginationRepositoryTest {

    @Autowired
    private CheckInRecordRepository checkInRecordRepository;

    @BeforeEach
    void clearCheckIns() {
        checkInRecordRepository.deleteAll();
    }

    @Test
    void userHistoryUsesBoundedStablePagesAndReportsWhetherMoreRowsExist() {
        LocalDateTime earlier = LocalDateTime.of(2026, 10, 1, 18, 0);
        LocalDateTime latest = LocalDateTime.of(2026, 10, 3, 18, 0);
        checkInRecordRepository.saveAndFlush(record(7L, earlier, "earlier"));
        CheckInRecord sameTimeLowerId = checkInRecordRepository.saveAndFlush(record(7L, latest, "same-time-1"));
        CheckInRecord sameTimeHigherId = checkInRecordRepository.saveAndFlush(record(7L, latest, "same-time-2"));
        checkInRecordRepository.saveAndFlush(record(8L, LocalDateTime.of(2026, 10, 4, 18, 0), "other-user"));

        Slice<CheckInRecord> firstPage = checkInRecordRepository
                .findByUserIdOrderByCheckedInAtDescIdDesc(7L, PageRequest.of(0, 2));
        Slice<CheckInRecord> secondPage = checkInRecordRepository
                .findByUserIdOrderByCheckedInAtDescIdDesc(7L, PageRequest.of(1, 2));

        assertThat(firstPage.getContent())
                .extracting(CheckInRecord::getId)
                .containsExactly(sameTimeHigherId.getId(), sameTimeLowerId.getId());
        assertThat(firstPage.hasNext()).isTrue();
        assertThat(secondPage.getContent()).extracting(CheckInRecord::getLocation).containsExactly("earlier");
        assertThat(secondPage.hasNext()).isFalse();
    }

    @Test
    void oversizedOffsetCannotBePassedToJpa() {
        assertThatThrownBy(() -> checkInRecordRepository.findByUserIdOrderByCheckedInAtDescIdDesc(
                7L, PageRequest.of(Integer.MAX_VALUE, 30)))
                .hasMessageContaining("offset");
    }

    private CheckInRecord record(Long userId, LocalDateTime checkedInAt, String location) {
        return CheckInRecord.builder()
                .partyId(10L)
                .userId(userId)
                .location(location)
                .checkedInAt(checkedInAt)
                .build();
    }
}
