package com.example.mate.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.mate.entity.ChatMessage;
import com.example.mate.repository.ChatMessageRepository;
import com.example.mate.service.ChatImageService;
import com.example.media.service.port.ChatMediaReferenceBatch;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

class ChatMediaMaintenanceAdapterTest {

    @Test
    void loadBatchMapsMateEntityToNeutralReference() {
        ChatMessageRepository repository = mock(ChatMessageRepository.class);
        ChatImageService chatImageService = mock(ChatImageService.class);
        ChatMessage message = ChatMessage.builder()
                .id(101L)
                .senderId(24L)
                .imageUrl("chat/24/legacy.png")
                .build();
        when(repository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(message)));
        ChatMediaMaintenanceAdapter adapter = new ChatMediaMaintenanceAdapter(repository, chatImageService);

        ChatMediaReferenceBatch batch = adapter.loadBatch(0, 100);

        assertThat(batch.hasNext()).isFalse();
        assertThat(batch.references()).singleElement().satisfies(reference -> {
            assertThat(reference.messageId()).isEqualTo(101L);
            assertThat(reference.senderId()).isEqualTo(24L);
            assertThat(reference.imageUrl()).isEqualTo("chat/24/legacy.png");
        });
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
        assertThat(pageable.getValue().getSort().getOrderFor("id"))
                .isNotNull()
                .extracting(Sort.Order::getDirection)
                .isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void normalizeAndUpdateDelegateToMateOwnedBehavior() {
        ChatMessageRepository repository = mock(ChatMessageRepository.class);
        ChatImageService chatImageService = mock(ChatImageService.class);
        ChatMessage message = ChatMessage.builder().id(101L).imageUrl("legacy").build();
        when(chatImageService.normalizeChatStoragePath("legacy"))
                .thenReturn("media/chat/24/70.webp");
        when(repository.findById(101L)).thenReturn(Optional.of(message));
        ChatMediaMaintenanceAdapter adapter = new ChatMediaMaintenanceAdapter(repository, chatImageService);

        String normalized = adapter.normalizeStoragePath("legacy");
        adapter.updateImageReference(101L, normalized);

        assertThat(normalized).isEqualTo("media/chat/24/70.webp");
        assertThat(message.getImageUrl()).isEqualTo("media/chat/24/70.webp");
        verify(repository).save(message);
    }
}
