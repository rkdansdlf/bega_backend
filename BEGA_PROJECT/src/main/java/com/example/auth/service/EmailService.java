package com.example.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.scheduling.JobScheduler;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final JobScheduler jobScheduler;

    public void sendPasswordResetEmail(String toEmail, String resetToken) {
        // JobRunr를 사용하여 백그라운드 작업으로 등록 (Fire-and-Forget)
        jobScheduler.enqueue(() -> sendPasswordResetEmailJob(toEmail, resetToken));
        log.info("Password reset email job enqueued for {}", toEmail);
    }

    /**
     * 실제 이메일 전송을 수행하는 백그라운드 작업
     * public이어야 JobRunr가 호출 가능
     */
    @Job(name = "Send Password Reset Email")
    public void sendPasswordResetEmailJob(String toEmail, String resetToken) {
        log.info("Starting email sending to {}", toEmail);
        String resetLink = "http://localhost:3000/password/reset/confirm?token=" + resetToken;

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
}