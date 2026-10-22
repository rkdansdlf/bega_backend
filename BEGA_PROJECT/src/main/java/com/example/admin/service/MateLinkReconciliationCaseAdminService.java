package com.example.admin.service;

import com.example.admin.dto.MateLinkReconciliationCaseDto;
import com.example.admin.dto.MateLinkReconciliationCasePageDto;
import com.example.admin.dto.PayoutHoldDto;
import com.example.admin.dto.PayoutHoldPageDto;
import com.example.mate.repository.MateLinkReconciliationCaseRepository;
import com.example.mate.repository.PayoutTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MateLinkReconciliationCaseAdminService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_PAGE_OFFSET = 100_000;
    private static final String MANUAL_PAYOUT_HOLD_REASON = "PAYOUT_MANUAL_RECONCILIATION_REQUIRED";

    private final MateLinkReconciliationCaseRepository caseRepository;
    private final PayoutTransactionRepository payoutTransactionRepository;

    @Transactional(readOnly = true)
    public MateLinkReconciliationCasePageDto list(int requestedPage, int requestedSize) {
        int pageSize = Math.max(1, Math.min(MAX_PAGE_SIZE, requestedSize));
        int maxPageNumber = MAX_PAGE_OFFSET / pageSize;
        int pageNumber = Math.max(0, Math.min(maxPageNumber, requestedPage));
        Page<MateLinkReconciliationCaseDto> page = caseRepository
                .findAllByOrderByObservedAtDescIdDesc(PageRequest.of(pageNumber, pageSize))
                .map(reconciliationCase -> new MateLinkReconciliationCaseDto(
                        reconciliationCase.getId(),
                        reconciliationCase.getPartyId(),
                        reconciliationCase.getApplicationId(),
                        reconciliationCase.getPaymentTransactionId(),
                        reconciliationCase.getReasonCode(),
                        reconciliationCase.getObservedAt()));
        return new MateLinkReconciliationCasePageDto(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }

    @Transactional(readOnly = true)
    public PayoutHoldPageDto listPayoutHolds(int requestedPage, int requestedSize) {
        int pageSize = Math.max(1, Math.min(MAX_PAGE_SIZE, requestedSize));
        int maxPageNumber = MAX_PAGE_OFFSET / pageSize;
        int pageNumber = Math.max(0, Math.min(maxPageNumber, requestedPage));
        Page<PayoutHoldDto> page = payoutTransactionRepository
                .findAllByFailureCodeOrderByUpdatedAtDescIdDesc(
                        MANUAL_PAYOUT_HOLD_REASON,
                        PageRequest.of(pageNumber, pageSize))
                .map(payout -> new PayoutHoldDto(
                        payout.getId(),
                        payout.getPaymentTransactionId(),
                        payout.getStatus(),
                        MANUAL_PAYOUT_HOLD_REASON,
                        payout.getUpdatedAt()));
        return new PayoutHoldPageDto(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
