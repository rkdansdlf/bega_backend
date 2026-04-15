package com.example.dm.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.common.exception.BadRequestBusinessException;
import com.example.dm.dto.DmMessageDto;
import com.example.dm.entity.DmMessage;
import com.example.dm.repository.DmMessageRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DmMessageService {

    private final DmMessageRepository dmMessageRepository;
    private final DmRoomService dmRoomService;

    @Transactional(readOnly = true)
    public List<DmMessageDto.Response> getMessages(Long roomId, Long currentUserId) {
        dmRoomService.getAccessibleRoom(roomId, currentUserId);
        return dmMessageRepository.findByRoomIdOrderByCreatedAtAsc(roomId).stream()
                .map(DmMessageDto.Response::from)
                .toList();
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
        return DmMessageDto.Response.from(savedMessage);
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
