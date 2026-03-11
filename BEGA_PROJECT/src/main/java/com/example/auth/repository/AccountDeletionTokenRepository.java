package com.example.auth.repository;

import com.example.auth.entity.AccountDeletionToken;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountDeletionTokenRepository extends JpaRepository<AccountDeletionToken, Long> {

    Optional<AccountDeletionToken> findByToken(String token);

    void deleteByUser_Id(Long userId);
}
