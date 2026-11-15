package com.example.mate.service;

import com.example.mate.entity.MateLinkReconciliationReasonCode;
import com.example.mate.repository.MateLinkReconciliationCaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MateLinkReconciliationCaseRecorder {

    private final MateLinkReconciliationCaseWriter caseWriter;
    private final MateLinkReconciliationCaseRepository caseRepository;

    public void record(
            Long partyId,
            Long applicationId,
            Long paymentTransactionId,
            MateLinkReconciliationReasonCode reasonCode) {
        String caseKey = caseKey(partyId, applicationId, paymentTransactionId, reasonCode);
        try {
            caseWriter.recordIfAbsent(
                    caseKey, partyId, applicationId, paymentTransactionId, reasonCode);
        } catch (DataIntegrityViolationException exception) {
            // A concurrent observer may have inserted the same deterministic key first.
            if (!caseRepository.existsByCaseKey(caseKey)) {
                throw exception;
            }
        }
    }

    private String caseKey(
            Long partyId,
            Long applicationId,
            Long paymentTransactionId,
            MateLinkReconciliationReasonCode reasonCode) {
        return reasonCode.name()
                + ":" + idPart(partyId)
                + ":" + idPart(applicationId)
                + ":" + idPart(paymentTransactionId);
    }

    private String idPart(Long id) {
        return id == null ? "-" : id.toString();
    }
}
