package com.example.kbo.repository;

import com.example.kbo.entity.GameMetadataEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GameMetadataRepository extends JpaRepository<GameMetadataEntity, String> {
    Optional<GameMetadataEntity> findByGameId(String gameId);
}
