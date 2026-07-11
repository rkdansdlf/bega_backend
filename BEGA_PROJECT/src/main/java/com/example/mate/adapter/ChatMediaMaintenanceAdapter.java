package com.example.mate.adapter;

import com.example.mate.entity.ChatMessage;
import com.example.mate.repository.ChatMessageRepository;
import com.example.mate.service.ChatImageService;
import com.example.media.service.port.ChatMediaReference;
import com.example.media.service.port.ChatMediaReferenceBatch;
import com.example.media.service.port.ChatMediaReferenceMaintenance;
import com.example.media.service.port.ChatMediaReferenceQuery;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ChatMediaMaintenanceAdapter
        implements ChatMediaReferenceQuery, ChatMediaReferenceMaintenance {

    private final ChatMessageRepository chatMessageRepository;
    private final ChatImageService chatImageService;

    @Override
    @Transactional(readOnly = true)
    public ChatMediaReferenceBatch loadBatch(int pageIndex, int batchSize) {
        Page<ChatMessage> page = chatMessageRepository.findAll(PageRequest.of(
                pageIndex,
                batchSize,
                Sort.by(Sort.Direction.ASC, "id")));
        List<ChatMediaReference> references = page.getContent().stream()
                .map(message -> new ChatMediaReference(
                        message.getId(),
                        message.getSenderId(),
                        message.getImageUrl()))
                .toList();
        return new ChatMediaReferenceBatch(references, page.hasNext());
    }

    @Override
    public String normalizeStoragePath(String pathOrUrl) {
        return chatImageService.normalizeChatStoragePath(pathOrUrl);
    }

    @Override
    @Transactional
    public void updateImageReference(Long messageId, String imageUrl) {
        ChatMessage message = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "채팅 메시지를 찾을 수 없습니다. id=" + messageId));
        message.setImageUrl(imageUrl);
        chatMessageRepository.save(message);
    }
}
