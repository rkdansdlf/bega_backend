package com.example.media.service;

import com.example.cheerboard.storage.strategy.StorageObjectIdentity;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Reference registration primitive; it does not establish that an audit or writer inventory is complete. */
@Service
@RequiredArgsConstructor
public class PhysicalMediaReferenceService {
    private final PhysicalMediaRegistry registry;
    private final JdbcTemplate jdbc;

    public record Reference(String sourceKind, String sourceId, java.util.UUID sourceGeneration, StorageObjectIdentity object) {}
    private record Source(String kind, String id, java.util.UUID generation) {}

    @Transactional(propagation = Propagation.MANDATORY)
    public void addAll(Collection<Reference> references) {
        Objects.requireNonNull(references, "references");
        var unique = new LinkedHashMap<Source, Reference>();
        for (Reference ref : references) {
            Objects.requireNonNull(ref, "reference");
            Objects.requireNonNull(ref.object(), "reference object");
            Objects.requireNonNull(ref.sourceGeneration(), "source generation");
            if (ref.sourceKind() == null || !ref.sourceKind().matches("[A-Z][A-Z0-9_]{0,31}")
                    || ref.sourceId() == null || ref.sourceId().isBlank() || ref.sourceId().length() > 128
                    || ref.sourceId().chars().anyMatch(Character::isISOControl)) {
                throw new IllegalArgumentException("Invalid physical media source identity");
            }
            var prior = unique.putIfAbsent(new Source(ref.sourceKind(), ref.sourceId(), ref.sourceGeneration()), ref);
            if (prior != null && !prior.object().equals(ref.object())) {
                throw new IllegalArgumentException("One source cannot refer to multiple physical objects");
            }
        }
        var locked = registry.lockOrRegisterAll(unique.values().stream().map(Reference::object).toList());
        for (var object : locked) {
            if (object.getState() != PhysicalObjectLifecycle.State.ACTIVE) {
                throw new IllegalStateException("Physical object cannot accept references");
            }
        }
        for (var ref : unique.values()) {
            List<String> existing = jdbc.query("SELECT object_hash FROM physical_media_refs WHERE source_kind = ? AND source_id = ? AND source_generation = ?",
                    (rs, row) -> rs.getString(1), ref.sourceKind(), ref.sourceId(), ref.sourceGeneration().toString());
            if (!existing.isEmpty()) {
                if (existing.size() != 1 || !existing.getFirst().equals(ref.object().digest())) {
                    throw new IllegalStateException("Physical media source is already bound to another object");
                }
                continue;
            }
            jdbc.update("INSERT INTO physical_media_refs (source_kind, source_id, source_generation, object_hash) VALUES (?, ?, ?, ?)",
                    ref.sourceKind(), ref.sourceId(), ref.sourceGeneration().toString(), ref.object().digest());
            int updated = jdbc.update("""
                    UPDATE physical_media_objects SET reference_revision = reference_revision + 1,
                        updated_at = CURRENT_TIMESTAMP
                    WHERE object_hash = ? AND state = 'ACTIVE' AND reference_revision < 9223372036854775807
                    """, ref.object().digest());
            if (updated != 1) throw new IllegalStateException("Physical media reference revision cannot advance");
        }
    }
}
