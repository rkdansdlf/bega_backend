package com.example.cheerboard.repo;

import com.example.cheerboard.domain.Team;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CheerTeamRepository extends JpaRepository<Team, String> {
	
}
