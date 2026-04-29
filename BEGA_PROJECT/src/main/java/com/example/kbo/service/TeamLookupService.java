package com.example.kbo.service;

import com.example.common.config.CacheConfig;
import com.example.kbo.dto.TeamSummaryDto;
import com.example.kbo.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 팀 목록 조회용 캐시 레이어.
 * 팀 데이터는 변경 빈도가 낮으므로 TEAM_DATA 캐시(30분 TTL, Redis)를 통해 응답한다.
 * 변경 발생 시 운영 콘솔의 캐시 invalidation 또는 TTL 만료를 기다린다.
 */
@Service
@RequiredArgsConstructor
public class TeamLookupService {

    private final TeamRepository teamRepository;

    @Cacheable(value = CacheConfig.TEAM_DATA, key = "'team-summaries-active'")
    @Transactional(readOnly = true)
    public List<TeamSummaryDto> getActiveTeamSummaries() {
        return teamRepository.findAllActiveTeams().stream()
                .map(TeamSummaryDto::from)
                .toList();
    }

    @Cacheable(value = CacheConfig.TEAM_DATA, key = "'team-summaries-all'")
    @Transactional(readOnly = true)
    public List<TeamSummaryDto> getAllTeamSummaries() {
        return teamRepository.findAll().stream()
                .map(TeamSummaryDto::from)
                .toList();
    }
}
