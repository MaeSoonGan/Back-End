package com.mock.maesoongan.authservice.auth;

import com.mock.maesoongan.authservice.common.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class EmailCodeSender {

    private static final Logger log = LoggerFactory.getLogger(EmailCodeSender.class);

    private final JavaMailSender mailSender;
    private final String host;
    private final String username;

    public EmailCodeSender(
            JavaMailSender mailSender,
            @Value("${spring.mail.host:localhost}") String host,
            @Value("${spring.mail.username:}") String username
    ) {
        this.mailSender = mailSender;
        this.host = host;
        this.username = username;
    }

    public void send(String email, String code) {
        if (isConsoleMode()) {
            log.info("[DEV EMAIL CODE] email={}, code={}", email, code);
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(username);
        message.setTo(email);
        message.setSubject("[MaeSoonGan] Email verification code");
        message.setText("Your verification code is " + code + ". It expires in 3 minutes.");
        try {
            mailSender.send(message);
        } catch (MailException exception) {
            log.warn("Failed to send email to {}", email, exception);
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to send email.");
        }
    }

    private boolean isConsoleMode() {
        return "localhost".equals(host) || !StringUtils.hasText(username);
    }
}
