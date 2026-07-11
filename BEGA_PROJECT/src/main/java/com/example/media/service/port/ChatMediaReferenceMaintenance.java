package com.example.media.service.port;

public interface ChatMediaReferenceMaintenance {

    String normalizeStoragePath(String pathOrUrl);

    void updateImageReference(Long messageId, String imageUrl);
}
