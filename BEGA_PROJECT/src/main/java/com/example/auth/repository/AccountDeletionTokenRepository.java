package com.example.auth.repository;

import com.example.auth.entity.AccountDeletionToken;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountDeletionTokenRepository extends JpaRepository<AccountDeletionToken, Long> {

    Optional<AccountDeletionToken> findByToken(String token);

    @Query("select deletionToken.user.id from AccountDeletionToken deletionToken where deletionToken.token = :token")
    Optional<Long> findUserIdByToken(@Param("token") String token);

    Optional<AccountDeletionToken> findByTokenAndUser_Id(String token, Long userId);

    void deleteByUser_Id(Long userId);
}
