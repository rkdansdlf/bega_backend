package com.example.demo.service;

import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {
    
    private final JavaMailSender mailSender;
    
    public void sendPasswordResetEmail(String toEmail, String resetToken) {
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
            "BEGA 팀"
        );
        
        mailSender.send(message);
    }
}