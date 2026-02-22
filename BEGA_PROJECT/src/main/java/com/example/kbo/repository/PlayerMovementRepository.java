package com.example.kbo.repository;

import com.example.kbo.entity.PlayerMovement;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface PlayerMovementRepository extends JpaRepository<PlayerMovement, Long> {

    // Recent movements sorted by movementDate desc
    List<PlayerMovement> findAllByOrderByMovementDateDesc();

    // Find movements within a specific offseason period
    List<PlayerMovement> findByMovementDateBetweenOrderByMovementDateDesc(LocalDate startDate, LocalDate endDate);

    // Find movements after a specific date
    List<PlayerMovement> findByMovementDateGreaterThanEqualOrderByMovementDateDesc(LocalDate startDate);
}
