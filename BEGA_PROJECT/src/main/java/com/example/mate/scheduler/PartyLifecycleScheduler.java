package com.example.mate.scheduler;

import com.example.mate.entity.Party;
import com.example.mate.entity.PartyApplication;
import com.example.mate.repository.PartyApplicationRepository;
import com.example.mate.repository.PartyRepository;
import com.example.notification.entity.Notification;
import com.example.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.scheduling.JobScheduler;
import org.jobrunr.scheduling.cron.Cron;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PartyLifecycleScheduler implements ApplicationRunner {

    private final PartyRepository partyRepository;
    private final PartyApplicationRepository applicationRepository;
    private final NotificationService notificationService;
    private final JobScheduler jobScheduler;

    @Override
    public void run(ApplicationArguments args) {
        // 매시간 정각에 실행 (파티 생명주기 관리)
        jobScheduler.scheduleRecurrently("party-lifecycle-management", Cron.hourly(), this::managePartyLifecycle);

        // 매일 오전 9시 실행 (내일 경기 알림)
        jobScheduler.scheduleRecurrently("game-tomorrow-reminder", Cron.daily(9, 0), this::sendGameTomorrowReminders);

        // 매일 오전 10시 실행 (오늘 경기 알림)
        jobScheduler.scheduleRecurrently("game-day-reminder", Cron.daily(10, 0), this::sendGameDayReminders);

        // 매 6시간 실행 (호스트 응답 넛지) - 0시, 6시, 12시, 18시
        jobScheduler.scheduleRecurrently("host-response-nudge", "0 */6 * * *", this::sendHostResponseNudges);
    }

    /**
     * 매시간 실행: 파티 생명주기 관리
     * - PENDING 파티 중 경기 날짜가 지난 것 → FAILED
     * - MATCHED 파티 중 어제 경기였는데 체크인 안 한 것 → COMPLETED
     * - CHECKED_IN 파티 중 경기 시간이 3시간 이상 지난 것 → COMPLETED
     * - 응답 기한이 지난 미처리 신청 → 자동 거절
     */
    @Job(name = "Manage Party Lifecycle")
    @Transactional
    public void managePartyLifecycle() {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        Instant now = Instant.now();
        Instant threeHoursAgo = now.minusSeconds(3 * 60 * 60);

        int expiredCount = 0;
        int autoCompletedCount = 0;
        int autoRejectedCount = 0;

        // 1. PENDING 파티 만료 처리 (gameDate < today → FAILED)
        List<Party> pendingParties = partyRepository.findByStatusAndGameDateBefore(
                Party.PartyStatus.PENDING, today);

        for (Party party : pendingParties) {
            party.setStatus(Party.PartyStatus.FAILED);
            partyRepository.save(party);

            // 호스트에게 알림
            notificationService.createNotification(
                    party.getHostId(),
                    Notification.NotificationType.PARTY_EXPIRED,
                    "파티 모집 만료",
                    "경기 날짜가 지나 파티가 자동 만료되었습니다.",
                    party.getId()
            );

            expiredCount++;
        }

        // 2. MATCHED 파티 중 어제 경기였는데 체크인 안 한 것 → COMPLETED
        List<Party> matchedYesterdayParties = partyRepository.findByStatusAndGameDate(
                Party.PartyStatus.MATCHED, yesterday);

        for (Party party : matchedYesterdayParties) {
            party.setStatus(Party.PartyStatus.COMPLETED);
            partyRepository.save(party);

            // 승인된 참여자들 조회
            List<PartyApplication> approvedApplicants = applicationRepository
                    .findByPartyIdAndIsApprovedTrue(party.getId());

            // 호스트에게 알림
            notificationService.createNotification(
                    party.getHostId(),
                    Notification.NotificationType.PARTY_AUTO_COMPLETED,
                    "파티 자동 완료",
                    "체크인 없이 경기가 종료되어 파티가 자동 완료 처리되었습니다.",
                    party.getId()
            );

            // 호스트에게 리뷰 요청 알림
            notificationService.createNotification(
                    party.getHostId(),
                    Notification.NotificationType.REVIEW_REQUEST,
                    "리뷰 요청",
                    "파티가 완료되었습니다. 참여자들에 대한 리뷰를 작성해주세요.",
                    party.getId()
            );

            // 승인된 참여자들에게도 알림
            for (PartyApplication app : approvedApplicants) {
                notificationService.createNotification(
                        app.getApplicantId(),
                        Notification.NotificationType.PARTY_AUTO_COMPLETED,
                        "파티 자동 완료",
                        "체크인 없이 경기가 종료되어 파티가 자동 완료 처리되었습니다.",
                        party.getId()
                );

                // 참여자에게 리뷰 요청 알림
                notificationService.createNotification(
                        app.getApplicantId(),
                        Notification.NotificationType.REVIEW_REQUEST,
                        "리뷰 요청",
                        "파티가 완료되었습니다. 호스트와 다른 참여자들에 대한 리뷰를 작성해주세요.",
                        party.getId()
                );
            }

            autoCompletedCount++;
        }

        // 3. CHECKED_IN 파티 중 경기 시간이 3시간 이상 지난 것 → COMPLETED
        List<Party> checkedInParties = partyRepository.findByStatus(Party.PartyStatus.CHECKED_IN);

        for (Party party : checkedInParties) {
            // 경기 날짜와 시간을 Instant로 변환
            LocalDateTime gameDateTime = LocalDateTime.of(party.getGameDate(), party.getGameTime());
            Instant gameInstant = gameDateTime.atZone(ZoneId.systemDefault()).toInstant();

            // 경기 시간이 3시간 이상 지났는지 확인
            if (gameInstant.isBefore(threeHoursAgo)) {
                party.setStatus(Party.PartyStatus.COMPLETED);
                partyRepository.save(party);

                // 승인된 참여자들 조회
                List<PartyApplication> approvedApplicants = applicationRepository
                        .findByPartyIdAndIsApprovedTrue(party.getId());

                // 호스트에게 알림
                notificationService.createNotification(
                        party.getHostId(),
                        Notification.NotificationType.PARTY_AUTO_COMPLETED,
                        "파티 완료",
                        "경기가 종료되어 파티가 완료 처리되었습니다.",
                        party.getId()
                );

                // 호스트에게 리뷰 요청 알림
                notificationService.createNotification(
                        party.getHostId(),
                        Notification.NotificationType.REVIEW_REQUEST,
                        "리뷰 요청",
                        "파티가 완료되었습니다. 참여자들에 대한 리뷰를 작성해주세요.",
                        party.getId()
                );

                // 승인된 참여자들에게도 알림
                for (PartyApplication app : approvedApplicants) {
                    notificationService.createNotification(
                            app.getApplicantId(),
                            Notification.NotificationType.PARTY_AUTO_COMPLETED,
                            "파티 완료",
                            "경기가 종료되어 파티가 완료 처리되었습니다.",
                            party.getId()
                    );

                    // 참여자에게 리뷰 요청 알림
                    notificationService.createNotification(
                            app.getApplicantId(),
                            Notification.NotificationType.REVIEW_REQUEST,
                            "리뷰 요청",
                            "파티가 완료되었습니다. 호스트와 다른 참여자들에 대한 리뷰를 작성해주세요.",
                            party.getId()
                    );
                }

                autoCompletedCount++;
            }
        }

        // 4. 응답 기한이 지난 미처리 신청 자동 거절
        List<PartyApplication> expiredApplications = applicationRepository
                .findByIsApprovedFalseAndIsRejectedFalseAndResponseDeadlineBefore(now);

        for (PartyApplication application : expiredApplications) {
            application.setIsRejected(true);
            application.setRejectedAt(now);
            applicationRepository.save(application);

            // 신청자에게 알림
            notificationService.createNotification(
                    application.getApplicantId(),
                    Notification.NotificationType.APPLICATION_REJECTED,
                    "신청 자동 거절",
                    "48시간 응답 기한이 지나 신청이 자동 거절되었습니다.",
                    application.getPartyId()
            );

            autoRejectedCount++;
        }

        if (expiredCount > 0 || autoCompletedCount > 0 || autoRejectedCount > 0) {
            log.info("Party Lifecycle Management completed - Expired: {}, Auto-completed: {}, Auto-rejected applications: {}",
                    expiredCount, autoCompletedCount, autoRejectedCount);
        }
    }

    /**
     * 매일 오전 9시 실행: 내일 경기 알림
     */
    @Job(name = "Send Game Tomorrow Reminders")
    @Transactional(readOnly = true)
    public void sendGameTomorrowReminders() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        // MATCHED 또는 CHECKED_IN 상태인 내일 경기 파티 조회
        List<Party> matchedParties = partyRepository.findByStatusAndGameDate(
                Party.PartyStatus.MATCHED, tomorrow);
        List<Party> checkedInParties = partyRepository.findByStatusAndGameDate(
                Party.PartyStatus.CHECKED_IN, tomorrow);

        int notificationCount = 0;

        // MATCHED 파티 알림
        for (Party party : matchedParties) {
            sendGameReminder(party, "내일 경기 알림", "내일 경기가 있습니다! 준비해주세요.");
            notificationCount++;
        }

        // CHECKED_IN 파티 알림
        for (Party party : checkedInParties) {
            sendGameReminder(party, "내일 경기 알림", "내일 경기가 있습니다! 준비해주세요.");
            notificationCount++;
        }

        if (notificationCount > 0) {
            log.info("Sent {} game tomorrow reminders", notificationCount);
        }
    }

    /**
     * 매일 오전 10시 실행: 오늘 경기 알림
     */
    @Job(name = "Send Game Day Reminders")
    @Transactional(readOnly = true)
    public void sendGameDayReminders() {
        LocalDate today = LocalDate.now();

        // MATCHED 또는 CHECKED_IN 상태인 오늘 경기 파티 조회
        List<Party> matchedParties = partyRepository.findByStatusAndGameDate(
                Party.PartyStatus.MATCHED, today);
        List<Party> checkedInParties = partyRepository.findByStatusAndGameDate(
                Party.PartyStatus.CHECKED_IN, today);

        int notificationCount = 0;

        // MATCHED 파티 알림
        for (Party party : matchedParties) {
            sendGameReminder(party, "오늘 경기 알림", "오늘 경기가 있습니다! 즐거운 관람 되세요!");
            notificationCount++;
        }

        // CHECKED_IN 파티 알림
        for (Party party : checkedInParties) {
            sendGameReminder(party, "오늘 경기 알림", "오늘 경기가 있습니다! 즐거운 관람 되세요!");
            notificationCount++;
        }

        if (notificationCount > 0) {
            log.info("Sent {} game day reminders", notificationCount);
        }
    }

    /**
     * 파티의 호스트와 승인된 참여자들에게 알림 전송
     */
    private void sendGameReminder(Party party, String title, String message) {
        // 호스트에게 알림
        notificationService.createNotification(
                party.getHostId(),
                Notification.NotificationType.GAME_DAY_REMINDER,
                title,
                message,
                party.getId()
        );

        // 승인된 참여자들에게 알림
        List<PartyApplication> approvedApplicants = applicationRepository
                .findByPartyIdAndIsApprovedTrue(party.getId());
        for (PartyApplication app : approvedApplicants) {
            notificationService.createNotification(
                    app.getApplicantId(),
                    Notification.NotificationType.GAME_DAY_REMINDER,
                    title,
                    message,
                    party.getId()
            );
        }
    }

    /**
     * 매 6시간 실행: 호스트 응답 넛지
     * 응답 기한이 24시간 이내인 미처리 신청에 대해 호스트에게 알림
     */
    @Job(name = "Send Host Response Nudges")
    @Transactional(readOnly = true)
    public void sendHostResponseNudges() {
        Instant now = Instant.now();
        Instant in24Hours = now.plusSeconds(24 * 60 * 60);

        List<PartyApplication> approachingDeadline = applicationRepository
                .findByIsApprovedFalseAndIsRejectedFalseAndResponseDeadlineBetween(now, in24Hours);

        int nudgeCount = 0;

        for (PartyApplication application : approachingDeadline) {
            try {
                Party party = partyRepository.findById(application.getPartyId()).orElse(null);
                if (party == null) continue;

                long hoursLeft = (application.getResponseDeadline().toEpochMilli() - now.toEpochMilli()) / (1000 * 60 * 60);

                notificationService.createNotification(
                        party.getHostId(),
                        Notification.NotificationType.HOST_RESPONSE_NUDGE,
                        "신청 응답을 기다리고 있어요",
                        application.getApplicantName() + "님의 신청이 대기 중입니다. " +
                                hoursLeft + "시간 후 자동 거절됩니다.",
                        party.getId()
                );
                nudgeCount++;
            } catch (Exception e) {
                log.error("호스트 넛지 알림 발송 실패: applicationId={}, error={}",
                        application.getId(), e.getMessage());
            }
        }

        if (nudgeCount > 0) {
            log.info("Sent {} host response nudge notifications", nudgeCount);
        }
    }
}
