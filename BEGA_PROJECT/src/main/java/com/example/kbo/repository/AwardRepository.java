package com.example.kbo.repository;

import com.example.kbo.entity.AwardEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AwardRepository extends JpaRepository<AwardEntity, Long> {
    List<AwardEntity> findByYear(int year);
}
