package com.example.admin.service;

import com.example.admin.dto.OffseasonMovementAdminDto;
import com.example.admin.dto.OffseasonMovementAdminRequest;
import com.example.admin.exception.OffseasonMovementNotFoundException;
import com.example.kbo.entity.PlayerMovement;
import com.example.kbo.repository.PlayerMovementRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OffseasonMovementAdminService {

    private final PlayerMovementRepository repository;

    public List<OffseasonMovementAdminDto> getMovements(
            String search,
            String section,
            String teamCode,
            LocalDate fromDate,
            LocalDate toDate) {
        String normalizedSearch = normalizeSearch(search);
        String normalizedSection = normalizeText(section);
        String normalizedTeamCode = normalizeCode(teamCode);

        return repository.findAllByOrderByMovementDateDesc().stream()
                .filter(movement -> matchesSearch(movement, normalizedSearch))
                .filter(movement -> matchesExact(normalizedSection, movement.getSection()))
                .filter(movement -> matchesExact(normalizedTeamCode, movement.getTeamCode()))
                .filter(movement -> matchesDateRange(movement.getMovementDate(), fromDate, toDate))
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public OffseasonMovementAdminDto createMovement(OffseasonMovementAdminRequest request) {
        PlayerMovement movement = new PlayerMovement();
        applyRequest(movement, request);
        return toDto(repository.save(movement));
    }

    @Transactional
    public OffseasonMovementAdminDto updateMovement(Long id, OffseasonMovementAdminRequest request) {
        PlayerMovement movement = repository.findById(id)
                .orElseThrow(() -> new OffseasonMovementNotFoundException(id));
        applyRequest(movement, request);
        return toDto(repository.save(movement));
    }

    @Transactional
    public void deleteMovement(Long id) {
        if (!repository.existsById(id)) {
            throw new OffseasonMovementNotFoundException(id);
        }
        repository.deleteById(id);
    }

    private void applyRequest(PlayerMovement movement, OffseasonMovementAdminRequest request) {
        movement.setMovementDate(request.getMovementDate());
        movement.setSection(normalizeText(request.getSection()));
        movement.setTeamCode(normalizeCode(request.getTeamCode()));
        movement.setPlayerName(normalizeText(request.getPlayerName()));
        movement.setSummary(normalizeText(request.getSummary()));
        movement.setDetails(normalizeText(request.getDetails()));
        movement.setContractTerm(normalizeText(request.getContractTerm()));
        movement.setContractValue(normalizeText(request.getContractValue()));
        movement.setOptionDetails(normalizeText(request.getOptionDetails()));
        movement.setCounterpartyTeam(normalizeCode(request.getCounterpartyTeam()));
        movement.setCounterpartyDetails(normalizeText(request.getCounterpartyDetails()));
        movement.setSourceLabel(normalizeText(request.getSourceLabel()));
        movement.setSourceUrl(normalizeText(request.getSourceUrl()));
        movement.setAnnouncedAt(request.getAnnouncedAt());
    }

    private OffseasonMovementAdminDto toDto(PlayerMovement movement) {
        return OffseasonMovementAdminDto.builder()
                .id(movement.getId())
                .movementDate(movement.getMovementDate())
                .section(movement.getSection())
                .teamCode(movement.getTeamCode())
                .playerName(movement.getPlayerName())
                .summary(movement.getSummary())
                .details(movement.getDetails())
                .contractTerm(movement.getContractTerm())
                .contractValue(movement.getContractValue())
                .optionDetails(movement.getOptionDetails())
                .counterpartyTeam(movement.getCounterpartyTeam())
                .counterpartyDetails(movement.getCounterpartyDetails())
                .sourceLabel(movement.getSourceLabel())
                .sourceUrl(movement.getSourceUrl())
                .announcedAt(movement.getAnnouncedAt())
                .createdAt(movement.getCreatedAt())
                .updatedAt(movement.getUpdatedAt())
                .build();
    }

    private boolean matchesSearch(PlayerMovement movement, String normalizedSearch) {
        if (!StringUtils.hasText(normalizedSearch)) {
            return true;
        }

        String haystack = String.join(" ",
                nullSafe(movement.getPlayerName()),
                nullSafe(movement.getTeamCode()),
                nullSafe(movement.getSection()),
                nullSafe(movement.getSummary()),
                nullSafe(movement.getDetails()),
                nullSafe(movement.getContractTerm()),
                nullSafe(movement.getContractValue()),
                nullSafe(movement.getOptionDetails()),
                nullSafe(movement.getCounterpartyTeam()),
                nullSafe(movement.getCounterpartyDetails()),
                nullSafe(movement.getSourceLabel()))
                .toLowerCase(Locale.ROOT);

        return haystack.contains(normalizedSearch);
    }

    private boolean matchesExact(String expected, String actual) {
        if (!StringUtils.hasText(expected)) {
            return true;
        }

        return expected.equalsIgnoreCase(nullSafe(actual).trim());
    }

    private boolean matchesDateRange(LocalDate value, LocalDate fromDate, LocalDate toDate) {
        if (value == null) {
            return false;
        }
        if (fromDate != null && value.isBefore(fromDate)) {
            return false;
        }
        if (toDate != null && value.isAfter(toDate)) {
            return false;
        }
        return true;
    }

    private String normalizeSearch(String value) {
        String normalized = normalizeText(value);
        if (normalized == null) {
            return null;
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private String normalizeCode(String value) {
        String normalized = normalizeText(value);
        if (normalized == null) {
            return null;
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    private String normalizeText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
