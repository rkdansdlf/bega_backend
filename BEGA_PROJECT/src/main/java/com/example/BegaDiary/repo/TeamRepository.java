package com.example.demo.repo;

import com.example.demo.entity.TeamEntity;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TeamRepository extends JpaRepository<TeamEntity, String> {
	Optional<TeamEntity> findByTeamId(String teamId);
}
