package com.compartix.backend.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Envío de correos de notificación. Deshabilitado por defecto
 * (app.mail.enabled=false); nunca interrumpe el flujo principal:
 * si el envío falla solo se registra en el log.
 */
@Service
@RequiredArgsConstructor
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    // ObjectProvider porque el bean JavaMailSender solo existe si spring.mail.host está configurado
    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${app.mail.enabled:false}")
    private boolean enabled;

    @Value("${spring.mail.username:}")
    private String remitente;

    @Async
    public void enviar(String para, String asunto, String cuerpo) {
        if (!enabled || para == null || para.isBlank()) {
            return;
        }
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            log.warn("app.mail.enabled=true pero spring.mail.* no está configurado; no se envía correo");
            return;
        }
        try {
            SimpleMailMessage mensaje = new SimpleMailMessage();
            mensaje.setTo(para);
            mensaje.setSubject("CompartiX — " + asunto);
            mensaje.setText(cuerpo + "\n\n—\nEste es un mensaje automático de CompartiX.");
            if (!remitente.isBlank()) {
                mensaje.setFrom(remitente);
            }
            sender.send(mensaje);
        } catch (Exception e) {
            log.warn("No se pudo enviar el correo a {}: {}", para, e.getMessage());
        }
    }
}
