package com.example.cheerboard.dto;

import java.util.Objects;

public record LinkedContentRes(
        LinkedContentKind kind,
        boolean available,
        LinkedContentUnavailableReason unavailableReason,
        CheckinLinkedContentRes checkin,
        RecruitmentLinkedContentRes recruitment) {

    public LinkedContentRes {
        Objects.requireNonNull(kind, "kind");
        if (available) {
            if (unavailableReason != null) {
                throw new IllegalArgumentException("Available linked content cannot have an unavailable reason");
            }
            boolean validCheckin = kind == LinkedContentKind.CHECKIN && checkin != null && recruitment == null;
            boolean validRecruitment = kind == LinkedContentKind.RECRUITMENT && checkin == null && recruitment != null;
            if (!validCheckin && !validRecruitment) {
                throw new IllegalArgumentException("Available linked content must contain exactly its matching variant");
            }
        } else {
            Objects.requireNonNull(unavailableReason, "unavailableReason");
            if (checkin != null || recruitment != null) {
                throw new IllegalArgumentException("Unavailable linked content cannot contain a preview variant");
            }
        }
    }

    public static LinkedContentRes availableCheckin(CheckinLinkedContentRes checkin) {
        return new LinkedContentRes(
                LinkedContentKind.CHECKIN, true, null, Objects.requireNonNull(checkin, "checkin"), null);
    }

    public static LinkedContentRes availableRecruitment(RecruitmentLinkedContentRes recruitment) {
        return new LinkedContentRes(
                LinkedContentKind.RECRUITMENT, true, null, null,
                Objects.requireNonNull(recruitment, "recruitment"));
    }

    public static LinkedContentRes unavailable(
            LinkedContentKind kind,
            LinkedContentUnavailableReason reason) {
        return new LinkedContentRes(kind, false, Objects.requireNonNull(reason, "reason"), null, null);
    }
}
