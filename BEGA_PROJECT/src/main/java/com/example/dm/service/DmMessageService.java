package com.example.dm.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.common.exception.BadRequestBusinessException;
import com.example.common.exception.ForbiddenBusinessException;
import com.example.common.exception.NotFoundBusinessException;
import com.example.common.realtime.RealtimeOutboxWriter;
import com.example.dm.dto.DmMessageDto;
import com.example.dm.entity.DmMessage;
import com.example.dm.repository.DmMessageRepository;
import com.example.dm.repository.DmRoomRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DmMessageService {

    private final DmMessageRepository dmMessageRepository;
    private final DmRoomRepository dmRoomRepository;
    private final DmRoomService dmRoomService;
    private final RealtimeOutboxWriter realtimeOutboxWriter;

    @Transactional
    public List<DmMessageDto.Response> getMessages(Long roomId, Long currentUserId) {
        dmRoomService.getAccessibleRoom(roomId, currentUserId);
        List<DmMessageDto.Response> messages = dmMessageRepository.findByRoomIdOrderByCreatedAtAsc(roomId).stream()
                .map(DmMessageDto.Response::from)
                .toList();
        dmRoomService.markAsRead(roomId, currentUserId);
        return messages;
    }

    @Transactional
    public DmMessageDto.Response sendMessage(Long currentUserId, DmMessageDto.Request request) {
        String normalizedContent = normalizeContent(request.getContent());
        dmRoomService.getAccessibleRoom(request.getRoomId(), currentUserId);

        if (request.getClientMessageId() != null && !request.getClientMessageId().isBlank()) {
            DmMessage existingMessage = dmMessageRepository.findByRoomIdAndSenderIdAndClientMessageId(
                    request.getRoomId(),
                    currentUserId,
                    request.getClientMessageId()).orElse(null);
            if (existingMessage != null) {
                return DmMessageDto.Response.from(existingMessage);
            }
        }

        DmMessage savedMessage = dmMessageRepository.save(DmMessage.builder()
                .roomId(request.getRoomId())
                .senderId(currentUserId)
                .content(normalizedContent)
                .clientMessageId(blankToNull(request.getClientMessageId()))
                .build());
        DmMessageDto.Response response = DmMessageDto.Response.from(savedMessage);
        realtimeOutboxWriter.broadcast("/topic/dm/" + response.getRoomId(), response);
        return response;
    }

    @Transactional
    public Long deleteMessage(Long messageId, Long currentUserId) {
        DmMessage message = dmMessageRepository.findById(messageId)
                .orElseThrow(() -> new NotFoundBusinessException("DM_MESSAGE_NOT_FOUND", "메시지를 찾을 수 없습니다."));
        // Non-participants get 404 (same as not-found) to prevent message-ID enumeration
        dmRoomRepository.findAccessibleByIdAndParticipantId(message.getRoomId(), currentUserId)
                .orElseThrow(() -> new NotFoundBusinessException("DM_MESSAGE_NOT_FOUND", "메시지를 찾을 수 없습니다."));
        if (!message.getSenderId().equals(currentUserId)) {
            throw new ForbiddenBusinessException("DM_DELETE_FORBIDDEN", "본인의 메시지만 삭제할 수 있습니다.");
        }
        Long roomId = message.getRoomId();
        dmMessageRepository.delete(message);
        realtimeOutboxWriter.broadcast(
                "/topic/dm/" + roomId,
                Map.of("messageId", messageId, "deleted", true, "roomId", roomId));
        return roomId;
    }

    private String normalizeContent(String content) {
        String trimmed = content == null ? "" : content.trim();
        if (trimmed.isEmpty()) {
            throw new BadRequestBusinessException("DM_MESSAGE_REQUIRED", "메시지 내용을 입력해 주세요.");
        }
        return trimmed;
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}
