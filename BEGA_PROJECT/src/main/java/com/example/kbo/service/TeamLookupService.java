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
 * teams 테이블이 비어 있을 때의 빈 결과는 캐시하지 않아, 데이터를 채운 뒤 바로 반영되게 한다.
 */
@Service
@RequiredArgsConstructor
public class TeamLookupService {

    private final TeamRepository teamRepository;

    @Cacheable(value = CacheConfig.TEAM_DATA, key = "'team-summaries-active'", unless = "#result == null || #result.isEmpty()")
    @Transactional(readOnly = true)
    public List<TeamSummaryDto> getActiveTeamSummaries() {
        return teamRepository.findAllActiveTeams().stream()
                .map(TeamSummaryDto::from)
                .toList();
    }

    @Cacheable(value = CacheConfig.TEAM_DATA, key = "'team-summaries-all'", unless = "#result == null || #result.isEmpty()")
    @Transactional(readOnly = true)
    public List<TeamSummaryDto> getAllTeamSummaries() {
        return teamRepository.findAll().stream()
                .map(TeamSummaryDto::from)
                .toList();
    }
}
