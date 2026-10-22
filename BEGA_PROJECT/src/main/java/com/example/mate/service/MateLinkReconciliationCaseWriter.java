package com.example.mate.service;

import com.example.mate.entity.MateLinkReconciliationCase;
import com.example.mate.entity.MateLinkReconciliationReasonCode;
import com.example.mate.repository.MateLinkReconciliationCaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MateLinkReconciliationCaseWriter {

    private final MateLinkReconciliationCaseRepository caseRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordIfAbsent(
            String caseKey,
            Long partyId,
            Long applicationId,
            Long paymentTransactionId,
            MateLinkReconciliationReasonCode reasonCode) {
        if (caseRepository.existsByCaseKey(caseKey)) {
            return;
        }
        caseRepository.saveAndFlush(MateLinkReconciliationCase.builder()
                .caseKey(caseKey)
                .partyId(partyId)
                .applicationId(applicationId)
                .paymentTransactionId(paymentTransactionId)
                .reasonCode(reasonCode)
                .build());
    }
}
