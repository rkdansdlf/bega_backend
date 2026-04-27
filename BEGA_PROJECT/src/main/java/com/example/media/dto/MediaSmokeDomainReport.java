package com.example.media.dto;

import com.example.media.entity.MediaDomain;
import java.util.List;

public record MediaSmokeDomainReport(
        MediaDomain domain,
        int checkedCount,
        int missingObjectCount,
        int urlFailureCount,
        int feedDerivativeMissingCount,
        List<String> failedObjectKeys) {
}
