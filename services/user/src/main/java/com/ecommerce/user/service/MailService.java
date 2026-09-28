package com.ecommerce.user.service;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender mailSender;
    private final String from;
    private final String fromName;
    private final String verifyBaseUrl;

    public MailService(JavaMailSender mailSender,
                       @Value("${app.mail.from}") String from,
                       @Value("${app.mail.from-name}") String fromName,
                       @Value("${app.verify.base-url}") String verifyBaseUrl) {
        this.mailSender = mailSender;
        this.from = from;
        this.fromName = fromName;
        this.verifyBaseUrl = verifyBaseUrl;
    }

    @Async
    public void sendVerificationEmail(String to, String username, String rawToken) {
        String link = verifyBaseUrl + "/verify?token=" + rawToken;
        String subject = fromName + ": confirma tu cuenta";
        String text = "Hola " + username + ",\n\n"
                + "Para activar tu cuenta pulsa el siguiente enlace:\n" + link
                + "\n\nSi no has creado una cuenta, ignora este correo.\n"
                + fromName;
        String html = "<div style=\"font-family:Segoe UI,Arial,sans-serif;background:#f4f4f8;padding:28px;border-radius:14px;max-width:480px;margin:auto\">"
                + "<h2 style=\"margin:0 0 12px\">" + esc(fromName) + "</h2>"
                + "<p style=\"margin:14px 0;color:#333\">Hola <strong>" + esc(username) + "</strong>,</p>"
                + "<p style=\"margin:14px 0;color:#333\">Para <strong>activar tu cuenta</strong> pulsa el botón:</p>"
                + "<a href=\"" + esc(link) + "\" style=\"display:inline-block;background:linear-gradient(135deg,#6366f1,#ec4899);color:#fff;padding:12px 22px;border-radius:10px;text-decoration:none;font-weight:600\">Confirmar mi cuenta</a>"
                + "<p style=\"margin:18px 0 0;color:#888;font-size:13px\">Si el botón no funciona, copia este enlace en tu navegador:<br><code style=\"color:#555\">" + esc(link) + "</code></p>"
                + "<p style=\"margin:16px 0 0;font-size:13px;color:#888\">Si no te has registrado, ignora este correo.</p>"
                + "</div>";

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from, fromName);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text, html);
            mailSender.send(message);
            log.info("Verification email sent to {}", to);
        } catch (Exception e) {
            log.error("Failed to send verification email to {}", to, e);
        }
    }

    private static String esc(String value) {
        return String.valueOf(value)
                .replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}