package com.example.docplatform.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import jakarta.mail.internet.MimeMessage;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EmailNotificationService {

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;

    public void sendReportReady(List<String> recipients, String documentId,
                                 String fileFormat, String downloadUrl) {
        Context ctx = new Context();
        ctx.setVariable("documentId", documentId);
        ctx.setVariable("fileFormat", fileFormat);
        ctx.setVariable("downloadUrl", downloadUrl);
        String html = templateEngine.process("email/report-ready", ctx);

        MimeMessage msg = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
            helper.setSubject("Your report is ready");
            helper.setText(html, true);
            for (String recipient : recipients) {
                helper.setTo(recipient);
                mailSender.send(msg);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to send email notification", e);
        }
    }
}
