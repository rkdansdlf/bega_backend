package com.example.demo.repo;

import com.example.demo.entity.AwardEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AwardRepository extends JpaRepository<AwardEntity, Long> {
    List<AwardEntity> findByYear(int year);
}
