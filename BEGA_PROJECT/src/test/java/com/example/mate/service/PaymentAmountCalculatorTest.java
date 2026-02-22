package com.example.mate.service;

import com.example.mate.entity.Party;
import com.example.mate.entity.PaymentFlowType;
import com.example.mate.exception.InvalidApplicationStatusException;
import com.example.mate.repository.PartyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class PaymentAmountCalculatorTest {

    @Mock
    private PartyRepository partyRepository;

    @InjectMocks
    private PaymentAmountCalculator calculator;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(calculator, "depositAmount", 10000);
    }

    @Test
    void calculateDepositAmount_success() {
        Party party = Party.builder()
                .id(1L)
                .stadium("잠실야구장")
                .ticketPrice(25000)
                .status(Party.PartyStatus.PENDING)
                .build();
        given(partyRepository.findById(1L)).willReturn(Optional.of(party));

        PaymentAmountCalculator.AmountInfo info = calculator.calculateDepositAmount(1L);

        assertThat(info.amount()).isEqualTo(35000);
        assertThat(info.currency()).isEqualTo("KRW");
        assertThat(info.orderName()).contains("잠실야구장");
    }

    @Test
    void calculateDepositAmount_nullTicketPrice_throws() {
        Party party = Party.builder()
                .id(1L)
                .stadium("잠실야구장")
                .ticketPrice(null)
                .status(Party.PartyStatus.PENDING)
                .build();
        given(partyRepository.findById(1L)).willReturn(Optional.of(party));

        assertThatThrownBy(() -> calculator.calculateDepositAmount(1L))
                .isInstanceOf(InvalidApplicationStatusException.class)
                .hasMessageContaining("티켓 가격이 설정되지 않아");
    }

    @Test
    void calculateDepositAmount_sellingParty_throws() {
        Party party = Party.builder()
                .id(1L)
                .stadium("잠실야구장")
                .ticketPrice(25000)
                .status(Party.PartyStatus.SELLING)
                .build();
        given(partyRepository.findById(1L)).willReturn(Optional.of(party));

        assertThatThrownBy(() -> calculator.calculateDepositAmount(1L))
                .isInstanceOf(InvalidApplicationStatusException.class)
                .hasMessageContaining("Toss 보증금 결제 흐름");
    }

    @Test
    void calculateAmount_sellingFull_success() {
        Party party = Party.builder()
                .id(1L)
                .stadium("잠실야구장")
                .price(42000)
                .ticketPrice(25000)
                .status(Party.PartyStatus.SELLING)
                .build();
        given(partyRepository.findById(1L)).willReturn(Optional.of(party));

        PaymentAmountCalculator.AmountInfo info = calculator.calculateAmount(1L, PaymentFlowType.SELLING_FULL);

        assertThat(info.amount()).isEqualTo(42000);
        assertThat(info.currency()).isEqualTo("KRW");
        assertThat(info.orderName()).contains("티켓 구매");
    }
}
