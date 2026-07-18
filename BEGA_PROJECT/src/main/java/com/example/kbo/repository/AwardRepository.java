package com.example.kbo.repository;

import com.example.kbo.entity.AwardEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AwardRepository extends JpaRepository<AwardEntity, Long> {
    List<AwardEntity> findByYear(int year);
}
