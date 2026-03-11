package com.example.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.scheduling.JobScheduler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final JobScheduler jobScheduler;

    public EmailService(JavaMailSender mailSender, ObjectProvider<JobScheduler> jobSchedulerProvider) {
        this.mailSender = mailSender;
        this.jobScheduler = jobSchedulerProvider.getIfAvailable();
    }

    @org.springframework.beans.factory.annotation.Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    public void sendPasswordResetEmail(String toEmail, String resetToken) {
        if (jobScheduler != null) {
            try {
                jobScheduler.enqueue((EmailService emailService) ->
                        emailService.sendPasswordResetEmailJob(toEmail, resetToken));
                log.info("Password reset email job enqueued for {}", toEmail);
                return;
            } catch (RuntimeException e) {
                log.warn("Failed to enqueue password reset email job for {}. Falling back to immediate send.", toEmail, e);
            }
        }

        log.warn("JobScheduler unavailable. Sending password reset email immediately for {}", toEmail);
        sendPasswordResetEmailJob(toEmail, resetToken);
    }

    /**
     * 실제 이메일 전송을 수행하는 백그라운드 작업
     * public이어야 JobRunr가 호출 가능
     */
    @Job(name = "Send Password Reset Email")
    public void sendPasswordResetEmailJob(String toEmail, String resetToken) {
        log.info("Starting email sending to {}", toEmail);
        String resetLink = frontendUrl + "/password/reset/confirm?token=" + resetToken;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("[BEGA] 비밀번호 재설정 요청");
        message.setText(
                "안녕하세요,\n\n" +
                        "비밀번호 재설정을 요청하셨습니다.\n\n" +
                        "아래 링크를 클릭하여 비밀번호를 재설정해주세요:\n" +
                        resetLink + "\n\n" +
                        "이 링크는 30분 동안 유효합니다.\n\n" +
                        "본인이 요청하지 않았다면 이 이메일을 무시하셔도 됩니다.\n\n" +
                        "감사합니다.\n" +
                        "BEGA 팀");

        try {
            mailSender.send(message);
            log.info("Email sent successfully to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send email to {}", toEmail, e);
            throw e; // 예외 발생 시 JobRunr가 재시도(Retry) 함
        }
    }

    public void sendNewDeviceLoginEmail(String toEmail, String deviceLabel, String browser, String os, String ipAddress) {
        if (jobScheduler != null) {
            try {
                jobScheduler.enqueue((EmailService emailService) ->
                        emailService.sendNewDeviceLoginEmailJob(toEmail, deviceLabel, browser, os, ipAddress));
                log.info("New device login email job enqueued for {}", toEmail);
                return;
            } catch (RuntimeException e) {
                log.warn("Failed to enqueue new device login email job for {}. Falling back to immediate send.", toEmail, e);
            }
        }

        sendNewDeviceLoginEmailJob(toEmail, deviceLabel, browser, os, ipAddress);
    }

    @Job(name = "Send New Device Login Email")
    public void sendNewDeviceLoginEmailJob(String toEmail, String deviceLabel, String browser, String os, String ipAddress) {
        String detectedAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"))
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        String accountSettingsLink = frontendUrl + "/mypage?view=accountSettings";

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("[BEGA] 새 기기 로그인 안내");
        message.setText(
                "안녕하세요,\n\n" +
                        "회원님의 계정에서 새 기기 로그인이 확인되었습니다.\n\n" +
                        "감지 시각: " + detectedAt + " (KST)\n" +
                        "기기: " + deviceLabel + "\n" +
                        "브라우저: " + browser + "\n" +
                        "OS: " + os + "\n" +
                        "IP: " + ipAddress + "\n\n" +
                        "확인이 필요하면 아래 경로에서 최근 보안 활동과 기기 목록을 점검해주세요:\n" +
                        accountSettingsLink + "\n\n" +
                        "본인이 로그인한 경우에는 별도 조치가 필요하지 않습니다.\n" +
                        "본인이 아니라면 비밀번호를 변경하고 다른 기기 로그아웃을 진행해주세요.\n\n" +
                        "감사합니다.\n" +
                        "BEGA 팀");

        try {
            mailSender.send(message);
            log.info("New device login email sent to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send new device login email to {}", toEmail, e);
            throw e;
        }
    }

    public void sendAccountDeletionRecoveryEmail(String toEmail, String recoveryToken, LocalDateTime scheduledFor) {
        if (jobScheduler != null) {
            try {
                jobScheduler.enqueue((EmailService emailService) ->
                        emailService.sendAccountDeletionRecoveryEmailJob(toEmail, recoveryToken, scheduledFor));
                log.info("Account deletion recovery email job enqueued for {}", toEmail);
                return;
            } catch (RuntimeException e) {
                log.warn("Failed to enqueue account deletion recovery email job for {}. Falling back to immediate send.", toEmail, e);
            }
        }

        sendAccountDeletionRecoveryEmailJob(toEmail, recoveryToken, scheduledFor);
    }

    @Job(name = "Send Account Deletion Recovery Email")
    public void sendAccountDeletionRecoveryEmailJob(String toEmail, String recoveryToken, LocalDateTime scheduledFor) {
        String recoveryLink = frontendUrl + "/account/deletion/recovery?token=" + recoveryToken;
        String requestedAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"))
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        String formattedSchedule = scheduledFor == null
                ? "예정된 삭제 시각 정보 없음"
                : scheduledFor.atZone(ZoneId.of("Asia/Seoul"))
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("[BEGA] 탈퇴 예약 접수 및 복구 링크 안내");
        message.setText(
                "안녕하세요,\n\n" +
                        "계정 탈퇴 예약이 접수되었습니다.\n\n" +
                        "요청 시각: " + requestedAt + " (KST)\n" +
                        "최종 삭제 예정 시각: " + formattedSchedule + " (KST)\n\n" +
                        "유예 기간 동안에는 로그인할 수 없으며, 아래 링크에서 탈퇴 예약을 취소할 수 있습니다:\n" +
                        recoveryLink + "\n\n" +
                        "본인이 요청하지 않았다면 위 링크를 열어 즉시 복구해주세요.\n" +
                        "유예 기간이 지나면 데이터 정리 절차가 시작되어 복구가 어려울 수 있습니다.\n\n" +
                        "감사합니다.\n" +
                        "BEGA 팀");

        try {
            mailSender.send(message);
            log.info("Account deletion recovery email sent to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send account deletion recovery email to {}", toEmail, e);
            throw e;
        }
    }
}
