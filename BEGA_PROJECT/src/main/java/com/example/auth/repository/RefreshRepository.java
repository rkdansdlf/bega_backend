package com.example.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.auth.entity.RefreshToken;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;

public interface RefreshRepository extends JpaRepository<RefreshToken, Long> {

    RefreshToken findByEmail(String email);

    RefreshToken findByToken(String token);

    List<RefreshToken> findAllByEmailOrderByIdDesc(String email);

    List<RefreshToken> findAllByEmail(String email);

    List<RefreshToken> findAllByEmailAndTokenNot(String email, String token);

    List<RefreshToken> findAllByToken(String token);

    @Query("""
            select r
              from RefreshToken r
             where lower(r.email) = lower(:email)
               and r.sessionId = :sessionId
             order by r.id desc
            """)
    List<RefreshToken> findAllByEmailAndSessionId(
            @Param("email") String email,
            @Param("sessionId") String sessionId);

    @Query("""
            select r
              from RefreshToken r
             where lower(r.email) = lower(:email)
               and r.deviceType = :deviceType
               and r.deviceLabel = :deviceLabel
               and r.browser = :browser
               and r.os = :os
               and r.ip = :ip
               and r.expiryDate >= :now
             order by r.lastSeenAt desc, r.id desc
            """)
    List<RefreshToken> findActiveByEmailAndSessionContextOrderByLastSeenDesc(
            @Param("email") String email,
            @Param("deviceType") String deviceType,
            @Param("deviceLabel") String deviceLabel,
            @Param("browser") String browser,
            @Param("os") String os,
            @Param("ip") String ip,
            @Param("now") LocalDateTime now);

    @Query("""
            select r
              from RefreshToken r
             where lower(r.email) = lower(:email)
               and r.deviceType = :deviceType
               and r.deviceLabel = :deviceLabel
               and r.browser = :browser
               and r.os = :os
               and r.ip = :ip
               and r.id <> :selectedId
             order by r.lastSeenAt desc, r.id desc
            """)
    List<RefreshToken> findDuplicateSessionContexts(
            @Param("email") String email,
            @Param("deviceType") String deviceType,
            @Param("deviceLabel") String deviceLabel,
            @Param("browser") String browser,
            @Param("os") String os,
            @Param("ip") String ip,
            @Param("selectedId") Long selectedId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RefreshToken r where r.token = :token")
    List<RefreshToken> findAllByTokenForUpdate(@Param("token") String token);

    int deleteByEmail(String email);
}
