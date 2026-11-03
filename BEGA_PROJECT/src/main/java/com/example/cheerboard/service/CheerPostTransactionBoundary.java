package com.example.cheerboard.service;

import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CheerPostTransactionBoundary {

    @Transactional
    public <T> T inWriteTransaction(Supplier<T> operation) {
        return operation.get();
    }

    @Transactional(readOnly = true)
    public <T> T inReadOnlyTransaction(Supplier<T> operation) {
        return operation.get();
    }
}
