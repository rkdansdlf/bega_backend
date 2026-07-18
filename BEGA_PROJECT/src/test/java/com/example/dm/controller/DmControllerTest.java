package com.example.dm.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.example.dm.dto.DmMessageDto;
import com.example.dm.service.DmMessageService;
import com.example.dm.service.DmRoomService;

class DmControllerTest {

    private final DmRoomService dmRoomService = mock(DmRoomService.class);
    private final DmMessageService dmMessageService = mock(DmMessageService.class);
    private final DmController controller = new DmController(
            dmRoomService,
            dmMessageService);

    @Test
    void sendMessageDelegatesDurablePublishToService() {
        DmMessageDto.Request request = DmMessageDto.Request.builder()
                .roomId(9L)
                .content("hello")
                .build();
        DmMessageDto.Response response = DmMessageDto.Response.builder()
                .id(11L)
                .roomId(9L)
                .content("hello")
                .build();
        when(dmMessageService.sendMessage(42L, request)).thenReturn(response);

        var result = controller.sendMessage(42L, request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        verify(dmMessageService).sendMessage(42L, request);
    }

    @Test
    void deleteMessageDelegatesDurablePublishToService() {
        when(dmMessageService.deleteMessage(11L, 42L)).thenReturn(9L);

        controller.deleteMessage(42L, 11L);

        verify(dmMessageService).deleteMessage(11L, 42L);
    }
}
