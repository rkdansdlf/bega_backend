package com.example.mate.service;

import com.example.mate.entity.Party;
import com.example.mate.entity.PaymentFlowType;
import com.example.mate.exception.InvalidApplicationStatusException;
import com.example.mate.exception.PartyNotFoundException;
import com.example.mate.repository.PartyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentAmountCalculator {

    private final PartyRepository partyRepository;

    @Value("${mate.payment.deposit-amount:10000}")
    private Integer depositAmount;

    public AmountInfo calculateAmount(Long partyId, PaymentFlowType flowType) {
        Party party = partyRepository.findById(partyId)
                .orElseThrow(() -> new PartyNotFoundException(partyId));
        return calculateAmount(party, flowType);
    }

    public AmountInfo calculateAmount(Party party, PaymentFlowType flowType) {
        PaymentFlowType resolvedFlowType = flowType == null ? PaymentFlowType.DEPOSIT : flowType;
        if (resolvedFlowType == PaymentFlowType.SELLING_FULL) {
            return calculateSellingAmount(party);
        }
        return calculateDepositAmount(party);
    }

    public AmountInfo calculateDepositAmount(Long partyId) {
        Party party = partyRepository.findById(partyId)
                .orElseThrow(() -> new PartyNotFoundException(partyId));
        return calculateDepositAmount(party);
    }

    public AmountInfo calculateDepositAmount(Party party) {
        if (party.getStatus() == Party.PartyStatus.SELLING) {
            throw new InvalidApplicationStatusException("티켓 판매 파티는 Toss 보증금 결제 흐름을 사용할 수 없습니다.");
        }
        if (party.getTicketPrice() == null) {
            throw new InvalidApplicationStatusException("티켓 가격이 설정되지 않아 결제를 진행할 수 없습니다.");
        }
        if (party.getTicketPrice() < 0) {
            throw new InvalidApplicationStatusException("티켓 가격이 올바르지 않습니다.");
        }

        int totalAmount = party.getTicketPrice() + depositAmount;
        if (totalAmount <= 0) {
            throw new InvalidApplicationStatusException("결제 금액이 올바르지 않습니다.");
        }

        String orderName = "KBO 메이트 결제 - " + party.getStadium();
        return new AmountInfo(party, totalAmount, "KRW", orderName);
    }

    public AmountInfo calculateSellingAmount(Party party) {
        if (party.getStatus() != Party.PartyStatus.SELLING) {
            throw new InvalidApplicationStatusException("SELLING 상태 파티만 전액 결제를 진행할 수 있습니다.");
        }
        if (party.getPrice() == null || party.getPrice() <= 0) {
            throw new InvalidApplicationStatusException("판매 가격이 설정되지 않아 결제를 진행할 수 없습니다.");
        }
        String orderName = "KBO 메이트 티켓 구매 - " + party.getStadium();
        return new AmountInfo(party, party.getPrice(), "KRW", orderName);
    }

    public record AmountInfo(Party party, Integer amount, String currency, String orderName) {
    }
}
