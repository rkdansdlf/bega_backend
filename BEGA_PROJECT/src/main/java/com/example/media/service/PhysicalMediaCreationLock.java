package com.example.media.service;

import com.example.media.repository.PhysicalMediaLockStripeRepository;
import java.util.HexFormat;
import java.util.Collection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Serializes first creation of registry rows; callers still lock and verify each full object row. */
@Service
@RequiredArgsConstructor
public class PhysicalMediaCreationLock {
    private final PhysicalMediaLockStripeRepository stripes;

    @Transactional(propagation = Propagation.MANDATORY)
    public void lock(String digest) {
        lockAll(java.util.Collections.singletonList(digest));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void lockAll(Collection<String> digests) {
        var ids = digests.stream().map(PhysicalMediaCreationLock::stripeId).distinct().sorted().toList();
        for (int id : ids) {
            stripes.findForWrite(id).orElseThrow(() -> new IllegalStateException("Physical media lock stripe is missing"));
        }
    }

    private static int stripeId(String digest) {
        if (digest == null || digest.length() != 64 || !digest.equals(digest.toLowerCase(java.util.Locale.ROOT))) {
            throw new IllegalArgumentException("A lowercase SHA-256 digest is required");
        }
        byte[] bytes;
        try {
            bytes = HexFormat.of().parseHex(digest);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("A lowercase SHA-256 digest is required", ex);
        }
        return Byte.toUnsignedInt(bytes[0]) % 64;
    }
}
