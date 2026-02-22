package com.example.mate.support;

import com.example.auth.entity.UserEntity;
import com.example.auth.entity.UserProvider;
import com.example.mate.entity.Party;
import com.example.mate.entity.PartyApplication;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Locale;
import java.util.UUID;

public final class MateTestFixtureFactory {

    private MateTestFixtureFactory() {
    }

    public static UserEntity user(String email, String name) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toLowerCase(Locale.ROOT);
        return UserEntity.builder()
                .uniqueId(UUID.randomUUID())
                .handle("@" + suffix)
                .name(name)
                .email(email.toLowerCase(Locale.ROOT))
                .password("encoded-password")
                .role("ROLE_USER")
                .provider("LOCAL")
                .build();
    }

    public static UserProvider socialProvider(UserEntity user, String provider) {
        String normalizedProvider = provider.toLowerCase(Locale.ROOT);
        return UserProvider.builder()
                .user(user)
                .provider(normalizedProvider)
                .providerId(normalizedProvider + "-" + UUID.randomUUID())
                .email(user.getEmail())
                .build();
    }

    public static Party pendingParty(Long hostId, String hostName, int maxParticipants) {
        return party(hostId, hostName, Party.PartyStatus.PENDING, maxParticipants, 1, 12000, null,
                LocalDate.now().plusDays(1), LocalTime.of(18, 30));
    }

    public static Party sellingParty(Long hostId, String hostName, int maxParticipants, int sellingPrice) {
        return party(hostId, hostName, Party.PartyStatus.SELLING, maxParticipants, 1, 12000, sellingPrice,
                LocalDate.now().plusDays(1), LocalTime.of(18, 30));
    }

    public static Party party(
            Long hostId,
            String hostName,
            Party.PartyStatus status,
            int maxParticipants,
            int currentParticipants,
            Integer ticketPrice,
            Integer sellingPrice,
            LocalDate gameDate,
            LocalTime gameTime) {

        return Party.builder()
                .hostId(hostId)
                .hostName(hostName)
                .hostBadge(Party.BadgeType.NEW)
                .hostRating(5.0)
                .teamId("LG")
                .gameDate(gameDate)
                .gameTime(gameTime)
                .stadium("잠실")
                .homeTeam("LG")
                .awayTeam("OB")
                .section("1루")
                .maxParticipants(maxParticipants)
                .currentParticipants(currentParticipants)
                .description("mate test party")
                .searchText("잠실 LG OB 1루 " + hostName + " mate test party")
                .ticketVerified(false)
                .status(status)
                .ticketPrice(ticketPrice)
                .price(sellingPrice)
                .build();
    }

    public static PartyApplication application(
            Long partyId,
            Long applicantId,
            String applicantName,
            PartyApplication.PaymentType paymentType,
            boolean paid,
            boolean approved,
            boolean rejected,
            String orderId,
            String paymentKey) {

        return PartyApplication.builder()
                .partyId(partyId)
                .applicantId(applicantId)
                .applicantName(applicantName)
                .applicantBadge(Party.BadgeType.NEW)
                .applicantRating(5.0)
                .message("test apply")
                .depositAmount(paymentType == PartyApplication.PaymentType.FULL ? 50000 : 22000)
                .paymentType(paymentType)
                .ticketVerified(false)
                .isPaid(paid)
                .isApproved(approved)
                .isRejected(rejected)
                .approvedAt(approved ? Instant.now() : null)
                .rejectedAt(rejected ? Instant.now() : null)
                .responseDeadline(Instant.now().plusSeconds(48 * 60 * 60))
                .orderId(orderId)
                .paymentKey(paymentKey)
                .build();
    }
}
