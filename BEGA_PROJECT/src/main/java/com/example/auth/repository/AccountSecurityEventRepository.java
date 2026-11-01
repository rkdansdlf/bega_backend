package com.example.auth.repository;

import com.example.auth.entity.AccountSecurityEvent;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountSecurityEventRepository extends JpaRepository<AccountSecurityEvent, Long> {

    List<AccountSecurityEvent> findTop20ByUserIdOrderByOccurredAtDesc(Long userId);

    void deleteByOccurredAtBefore(LocalDateTime cutoff);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AccountSecurityEvent event
               set event.userId = null,
                   event.deviceLabel = null,
                   event.deviceType = null,
                   event.browser = null,
                   event.os = null,
                   event.ip = null,
                   event.message = '계정 보안 이벤트'
             where event.userId = :userId
            """)
    int anonymizeByUserId(@Param("userId") Long userId);
}
