package com.example.demo.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.entity.TeamEntity;
import com.example.demo.entity.TeamFranchiseEntity;
import com.example.demo.repo.TeamFranchiseRepository;
import com.example.demo.repo.TeamRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

/**
 * TeamFranchiseService
 *
 * KBO 프랜차이즈 관련 비즈니스 로직을 처리하는 서비스
 * 10개 KBO 프랜차이즈의 정보, 역사, 현재 팀 정보를 제공
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeamFranchiseService {

    private static final Logger log = LoggerFactory.getLogger(TeamFranchiseService.class);

    private final TeamFranchiseRepository franchiseRepository;
    private final TeamRepository teamRepository;
    private final ObjectMapper objectMapper;

    /**
     * 모든 KBO 프랜차이즈 조회
     *
     * @return 10개 프랜차이즈 목록
     */
    public List<TeamFranchiseEntity> getAllFranchises() {
        log.debug("Fetching all KBO franchises");
        return franchiseRepository.findAll();
    }

    /**
     * ID로 프랜차이즈 조회
     *
     * @param id 프랜차이즈 ID
     * @return 프랜차이즈 엔티티
     */
    @SuppressWarnings("null")
    public Optional<TeamFranchiseEntity> getFranchiseById(Integer id) {
        log.debug("Fetching franchise with id: {}", id);
        return franchiseRepository.findById(id);
    }

    /**
     * 코드로 프랜차이즈 조회 (original_code 또는 current_code)
     *
     * @param code 팀 코드 (예: SS, OB, KIA 등)
     * @return 프랜차이즈 엔티티
     */
    public Optional<TeamFranchiseEntity> getFranchiseByCode(String code) {
        log.debug("Fetching franchise with code: {}", code);

        // original_code로 먼저 찾기
        Optional<TeamFranchiseEntity> franchise = franchiseRepository.findByOriginalCode(code);

        // 없으면 current_code로 찾기
        if (franchise.isEmpty()) {
            franchise = franchiseRepository.findByCurrentCode(code);
        }

        return franchise;
    }

    /**
     * 프랜차이즈의 모든 팀 조회 (역사적 팀 포함)
     *
     * @param franchiseId 프랜차이즈 ID
     * @return 팀 목록 (역사적 팀 + 현재 팀)
     */
    public List<TeamEntity> getTeamsByFranchiseId(Integer franchiseId) {
        log.debug("Fetching all teams for franchise id: {}", franchiseId);
        return teamRepository.findByFranchiseId(franchiseId);
    }

    /**
     * 프랜차이즈의 현재 활성 팀 조회
     *
     * @param franchiseId 프랜차이즈 ID
     * @return 현재 활성 팀
     */
    public Optional<TeamEntity> getCurrentTeamByFranchiseId(Integer franchiseId) {
        log.debug("Fetching current active team for franchise id: {}", franchiseId);
        return teamRepository.findByFranchiseIdAndIsActive(franchiseId, true);
    }

    /**
     * 프랜차이즈 메타데이터 조회 및 파싱
     *
     * metadata_json 필드를 Map으로 변환하여 반환
     *
     * @param franchiseId 프랜차이즈 ID
     * @return 메타데이터 Map (owner, ceo, address, homepage 등)
     */
    @SuppressWarnings("null")
    public Map<String, Object> getFranchiseMetadata(Integer franchiseId) {
        log.debug("Fetching and parsing metadata for franchise id: {}", franchiseId);

        return franchiseRepository.findById(franchiseId)
                .map(franchise -> {
                    String metadataJson = franchise.getMetadataJson();
                    if (metadataJson == null || metadataJson.trim().isEmpty()) {
                        return Map.<String, Object>of();
                    }

                    try {
                        return objectMapper.readValue(
                                metadataJson,
                                new TypeReference<Map<String, Object>>() {
                                });
                    } catch (Exception e) {
                        log.error("Failed to parse metadata JSON for franchise {}: {}",
                                franchiseId, e.getMessage());
                        return Map.<String, Object>of();
                    }
                })
                .orElse(Map.of());
    }

    /**
     * 프랜차이즈 이름으로 검색
     *
     * @param keyword 검색 키워드
     * @return 매칭되는 프랜차이즈 목록
     */
    public List<TeamFranchiseEntity> searchFranchisesByName(String keyword) {
        log.debug("Searching franchises with keyword: {}", keyword);
        return franchiseRepository.findByNameContaining(keyword);
    }
}
