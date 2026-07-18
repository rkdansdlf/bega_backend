package com.example.media.service.port;

import java.util.List;

public record ChatMediaReferenceBatch(List<ChatMediaReference> references, boolean hasNext) {

    public ChatMediaReferenceBatch {
        references = references == null ? List.of() : List.copyOf(references);
    }

    public static ChatMediaReferenceBatch empty() {
        return new ChatMediaReferenceBatch(List.of(), false);
    }
}
