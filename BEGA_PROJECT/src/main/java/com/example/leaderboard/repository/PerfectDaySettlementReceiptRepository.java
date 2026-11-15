package com.example.leaderboard.repository;

import com.example.leaderboard.entity.PerfectDaySettlementReceipt;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PerfectDaySettlementReceiptRepository extends JpaRepository<PerfectDaySettlementReceipt, Long> {

    boolean existsByUserIdAndGameDate(Long userId, LocalDate gameDate);
}
