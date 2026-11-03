package com.example.mate.repository;

import com.example.mate.entity.MateLinkReconciliationCase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MateLinkReconciliationCaseRepository
        extends JpaRepository<MateLinkReconciliationCase, Long> {

    boolean existsByCaseKey(String caseKey);

    Page<MateLinkReconciliationCase> findAllByOrderByObservedAtDescIdDesc(Pageable pageable);
}
