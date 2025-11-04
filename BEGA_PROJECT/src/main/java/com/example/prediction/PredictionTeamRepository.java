package com.example.demo.prediction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PredictionTeamRepository extends JpaRepository<PredictionTeamEntity, String> {
}
