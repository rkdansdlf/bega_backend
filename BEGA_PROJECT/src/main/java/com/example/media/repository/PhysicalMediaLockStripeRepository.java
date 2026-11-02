package com.example.media.repository;

import com.example.media.entity.PhysicalMediaLockStripe;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PhysicalMediaLockStripeRepository extends JpaRepository<PhysicalMediaLockStripe, Integer> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from PhysicalMediaLockStripe s where s.id = :id")
    Optional<PhysicalMediaLockStripe> findForWrite(@Param("id") int id);
}
