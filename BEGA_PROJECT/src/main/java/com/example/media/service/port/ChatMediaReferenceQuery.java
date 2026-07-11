package com.example.media.service.port;

@FunctionalInterface
public interface ChatMediaReferenceQuery {

    ChatMediaReferenceBatch loadBatch(int pageIndex, int batchSize);
}
