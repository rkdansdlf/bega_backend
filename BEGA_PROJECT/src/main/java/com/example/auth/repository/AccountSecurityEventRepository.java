package com.example.auth.repository;

import com.example.auth.entity.AccountSecurityEvent;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountSecurityEventRepository extends JpaRepository<AccountSecurityEvent, Long> {

    List<AccountSecurityEvent> findTop20ByUserIdOrderByOccurredAtDesc(Long userId);

    void deleteByOccurredAtBefore(LocalDateTime cutoff);
}
