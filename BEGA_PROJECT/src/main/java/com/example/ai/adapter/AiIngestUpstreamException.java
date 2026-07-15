package com.example.ai.adapter;

import org.springframework.http.HttpStatusCode;

public class AiIngestUpstreamException extends RuntimeException {

    public AiIngestUpstreamException(HttpStatusCode status, String errorCode) {
        super("AI ingestion upstream request failed: status=" + status.value() + " code=" + errorCode);
    }
}
