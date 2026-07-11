package com.example.BegaDiary.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.BegaDiary.Entity.SeatViewClassificationResult;
import com.example.BegaDiary.Service.port.SeatViewClassificationPort;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

class SeatViewClassificationServiceTest {

    @Test
    void classifyDelegatesToCapabilityPort() {
        SeatViewClassificationPort port = mock(SeatViewClassificationPort.class);
        SeatViewClassificationService service = new SeatViewClassificationService(port);
        MockMultipartFile file = new MockMultipartFile(
                "file", "seat.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[] {1});
        SeatViewClassificationResult expected = mock(SeatViewClassificationResult.class);
        when(port.classify(file)).thenReturn(expected);

        SeatViewClassificationResult result = service.classify(file);

        assertThat(result).isSameAs(expected);
        verify(port).classify(file);
    }
}
