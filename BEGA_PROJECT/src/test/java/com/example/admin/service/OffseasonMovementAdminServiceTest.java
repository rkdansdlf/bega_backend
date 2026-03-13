package com.example.admin.service;

import com.example.admin.dto.OffseasonMovementAdminDto;
import com.example.admin.dto.OffseasonMovementAdminRequest;
import com.example.admin.exception.OffseasonMovementNotFoundException;
import com.example.kbo.entity.PlayerMovement;
import com.example.kbo.repository.PlayerMovementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OffseasonMovementAdminServiceTest {

    @Mock
    private PlayerMovementRepository repository;

    private OffseasonMovementAdminService service;

    @BeforeEach
    void setUp() {
        service = new OffseasonMovementAdminService(repository);
    }

    @Test
    void createMovementShouldNormalizeStructuredFields() {
        OffseasonMovementAdminRequest request = new OffseasonMovementAdminRequest();
        request.setMovementDate(LocalDate.of(2025, 1, 5));
        request.setSection(" FA ");
        request.setTeamCode(" lg ");
        request.setPlayerName(" 홍길동 ");
        request.setSummary(" 4년 총액 80억에 잔류 ");
        request.setDetails(" 계약금 20억 포함 ");
        request.setContractTerm(" 4년 ");
        request.setContractValue(" 4년 80억 ");
        request.setOptionDetails(" ");
        request.setCounterpartyTeam(" ssg ");
        request.setCounterpartyDetails(" 보상선수 없음 ");
        request.setSourceLabel(" 구단 발표 ");
        request.setSourceUrl(" https://example.com/lg-hong ");
        request.setAnnouncedAt(LocalDateTime.of(2025, 1, 5, 10, 0));

        given(repository.save(any(PlayerMovement.class))).willAnswer(invocation -> {
            PlayerMovement movement = invocation.getArgument(0);
            movement.setId(11L);
            return movement;
        });

        OffseasonMovementAdminDto result = service.createMovement(request);

        ArgumentCaptor<PlayerMovement> captor = ArgumentCaptor.forClass(PlayerMovement.class);
        verify(repository).save(captor.capture());
        PlayerMovement saved = captor.getValue();

        assertEquals("FA", saved.getSection());
        assertEquals("LG", saved.getTeamCode());
        assertEquals("홍길동", saved.getPlayerName());
        assertEquals("4년 총액 80억에 잔류", saved.getSummary());
        assertEquals("계약금 20억 포함", saved.getDetails());
        assertEquals("4년", saved.getContractTerm());
        assertEquals("4년 80억", saved.getContractValue());
        assertNull(saved.getOptionDetails());
        assertEquals("SSG", saved.getCounterpartyTeam());
        assertEquals("보상선수 없음", saved.getCounterpartyDetails());
        assertEquals("구단 발표", saved.getSourceLabel());
        assertEquals("https://example.com/lg-hong", saved.getSourceUrl());
        assertEquals(11L, result.getId());
    }

    @Test
    void getMovementsShouldFilterBySearchAndTeam() {
        PlayerMovement match = PlayerMovement.builder()
                .id(1L)
                .movementDate(LocalDate.of(2025, 1, 10))
                .section("FA")
                .teamCode("LG")
                .playerName("홍길동")
                .summary("4년 총액 80억에 잔류")
                .details("원소속팀과 계약 완료")
                .build();
        PlayerMovement other = PlayerMovement.builder()
                .id(2L)
                .movementDate(LocalDate.of(2025, 1, 11))
                .section("트레이드")
                .teamCode("SSG")
                .playerName("김철수")
                .summary("삼성과 2대1 트레이드")
                .build();

        given(repository.findAllByOrderByMovementDateDesc()).willReturn(List.of(other, match));

        List<OffseasonMovementAdminDto> result = service.getMovements("80억", null, "lg", null, null);

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getId());
    }

    @Test
    void updateMovementShouldThrowNotFoundWhenMissing() {
        given(repository.findById(99L)).willReturn(Optional.empty());

        OffseasonMovementAdminRequest request = new OffseasonMovementAdminRequest();
        request.setMovementDate(LocalDate.of(2025, 2, 1));
        request.setSection("FA");
        request.setTeamCode("LG");
        request.setPlayerName("홍길동");

        OffseasonMovementNotFoundException exception = assertThrows(OffseasonMovementNotFoundException.class,
                () -> service.updateMovement(99L, request));

        assertEquals("OFFSEASON_MOVEMENT_NOT_FOUND", exception.getCode());
        assertEquals("이동 정보를 찾을 수 없습니다. id=99", exception.getMessage());
    }
}
