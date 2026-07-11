package com.example.cheerboard.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.example.cheerboard.service.port.RagIngestionPort;
import org.junit.jupiter.api.Test;

class AiIntegrationServiceTest {

    @Test
    void triggerRagIngestionDelegatesToCapabilityPort() {
        RagIngestionPort port = mock(RagIngestionPort.class);
        AiIntegrationService service = new AiIntegrationService(port);

        service.triggerRagIngestion();

        verify(port).trigger();
    }
}
