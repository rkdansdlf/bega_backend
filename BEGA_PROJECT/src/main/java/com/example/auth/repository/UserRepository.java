package com.example.auth.repository;

import com.example.auth.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Long> {

  Optional<UserEntity> findByHandle(String handle);

  Boolean existsByHandle(String handle);

  Optional<UserEntity> findByUniqueId(java.util.UUID uniqueId);

  Optional<UserEntity> findByName(String name);

  Boolean existsByEmail(String email);

  Optional<UserEntity> findByEmail(String email);

  @org.springframework.data.jpa.repository.EntityGraph(attributePaths = { "providers" })
  Optional<UserEntity> findWithProvidersByEmail(String email);

  List<UserEntity> findByEmailContainingOrNameContaining(String email, String name);

  List<UserEntity> findByEmailContainingOrNameContainingOrderByIdAsc(String email, String name);

  Optional<UserEntity> findByNameIgnoreCase(String name);

  List<UserEntity> findAllByOrderByIdAsc();

  @org.springframework.data.jpa.repository.Query("SELECT u.profileImageUrl FROM UserEntity u WHERE u.id = :userId")
  Optional<String> findProfileImageUrlById(@org.springframework.data.repository.query.Param("userId") Long userId);

  @org.springframework.data.jpa.repository.Query("SELECT u.profileFeedImageUrl FROM UserEntity u WHERE u.id = :userId")
  Optional<String> findProfileFeedImageUrlById(@org.springframework.data.repository.query.Param("userId") Long userId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT u FROM UserEntity u WHERE u.id = :userId")
  Optional<UserEntity> findByIdForWrite(@org.springframework.data.repository.query.Param("userId") Long userId);

  @Query("""
      SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END
      FROM UserEntity u
      WHERE u.id = :userId
        AND u.enabled = true
        AND (
          u.locked = false
          OR (
            u.lockExpiresAt IS NOT NULL
            AND u.lockExpiresAt < CURRENT_TIMESTAMP
          )
        )
      """)
  boolean existsUsableAuthorById(@Param("userId") Long userId);

  @Query("""
      SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END
      FROM UserEntity u
      WHERE u.id = :userId
        AND COALESCE(u.tokenVersion, 0) = :tokenVersion
        AND u.enabled = true
        AND (
          u.locked = false
          OR (
            u.lockExpiresAt IS NOT NULL
            AND u.lockExpiresAt < CURRENT_TIMESTAMP
          )
        )
      """)
  boolean existsUsableAuthorByIdAndTokenVersion(@Param("userId") Long userId,
      @Param("tokenVersion") int tokenVersion);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("""
      SELECT u.id
      FROM UserEntity u
      WHERE u.id = :userId
        AND u.enabled = true
        AND (
          u.locked = false
          OR (
            u.lockExpiresAt IS NOT NULL
            AND u.lockExpiresAt < CURRENT_TIMESTAMP
          )
        )
      """)
  java.util.Optional<Long> lockUsableAuthorForWrite(@Param("userId") Long userId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("""
      SELECT u.id
      FROM UserEntity u
      WHERE u.id = :userId
        AND COALESCE(u.tokenVersion, 0) = :tokenVersion
        AND u.enabled = true
        AND (
          u.locked = false
          OR (
            u.lockExpiresAt IS NOT NULL
            AND u.lockExpiresAt < CURRENT_TIMESTAMP
          )
        )
      """)
  java.util.Optional<Long> lockUsableAuthorForWriteWithTokenVersion(@Param("userId") Long userId,
      @Param("tokenVersion") int tokenVersion);

  @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
  @Transactional
  @org.springframework.data.jpa.repository.Query("UPDATE UserEntity u SET u.profileImageUrl = :profilePath WHERE u.id = :userId")
  int updateProfileImageUrlById(@org.springframework.data.repository.query.Param("userId") Long userId,
      @org.springframework.data.repository.query.Param("profilePath") String profilePath);

  @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
  @Transactional
  @org.springframework.data.jpa.repository.Query("UPDATE UserEntity u SET u.profileImageUrl = :profilePath, u.profileFeedImageUrl = :profileFeedImagePath WHERE u.id = :userId")
  int updateProfileImageUrlsById(@org.springframework.data.repository.query.Param("userId") Long userId,
      @org.springframework.data.repository.query.Param("profilePath") String profilePath,
      @org.springframework.data.repository.query.Param("profileFeedImagePath") String profileFeedImagePath);

  @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true)
  @Transactional
  @org.springframework.data.jpa.repository.Query("""
      UPDATE UserEntity u
      SET u.lastLoginDate = :lastLoginDate,
          u.cheerPoints = :cheerPoints
      WHERE u.id = :userId
      """)
  int updateLoginActivity(@org.springframework.data.repository.query.Param("userId") Long userId,
      @org.springframework.data.repository.query.Param("lastLoginDate") java.time.LocalDateTime lastLoginDate,
      @org.springframework.data.repository.query.Param("cheerPoints") int cheerPoints);

  @org.springframework.data.jpa.repository.Modifying
  @Transactional
  @org.springframework.data.jpa.repository.Query("UPDATE UserEntity u SET u.cheerPoints = COALESCE(u.cheerPoints, 0) + :points WHERE u.id = :userId")
  void modifyCheerPoints(@org.springframework.data.repository.query.Param("userId") Long userId,
      @org.springframework.data.repository.query.Param("points") int points);

  List<UserEntity> findByPendingDeletionTrueAndDeletionScheduledForLessThanEqual(java.time.LocalDateTime scheduledFor);
}
