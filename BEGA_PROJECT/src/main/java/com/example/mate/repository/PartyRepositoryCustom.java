package com.example.mate.repository;

import com.example.mate.entity.Party;
import com.example.mate.entity.Party.PartyStatus;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PartyRepositoryCustom {

        Page<Party> findPartiesWithFilter(
                        String teamId,
                        String stadium,
                        LocalDate gameDate,
                        String query,
                        List<Party.PartyStatus> excludedStatuses,
                        PartyStatus status,
                        Pageable pageable);

        Page<Party> findVisiblePublicPartiesWithFilter(
                        String teamId,
                        String stadium,
                        LocalDate gameDate,
                        String query,
                        List<Party.PartyStatus> excludedStatuses,
                        PartyStatus status,
                        Long currentUserId,
                        Pageable pageable);

        Page<Party> findMyHistory(
                        Long userId,
                        List<PartyStatus> statuses,
                        Pageable pageable);
}
