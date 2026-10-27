package com.sopaletras.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import com.sopaletras.config.AppProperties;

/**
 * Envía los correos de verificación y recuperación. Con {@code app.mail.enabled=false}
 * (desarrollo) solo escribe el enlace en el log para poder probar sin servidor de correo.
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final ObjectProvider<JavaMailSender> sender;
    private final AppProperties props;

    public MailService(ObjectProvider<JavaMailSender> sender, AppProperties props) {
        this.sender = sender;
        this.props = props;
    }

    public void sendVerification(String to, String alias, String token) {
        String link = props.webBaseUrl() + "/cuenta/verificar?token=" + token;
        send(to, "Confirma tu correo",
                "Hola" + (alias == null ? "" : " " + alias) + ",\n\n"
                        + "Para activar tu cuenta de Sopa de Letras abre este enlace:\n" + link + "\n\n"
                        + "El enlace vence en " + props.auth().verifyEmailTtl().toHours() + " horas.\n"
                        + "Si no creaste una cuenta, ignora este correo.", link);
    }

    public void sendPasswordReset(String to, String token) {
        String link = props.webBaseUrl() + "/cuenta/restablecer?token=" + token;
        send(to, "Restablece tu contraseña",
                "Recibimos una solicitud para cambiar tu contraseña de Sopa de Letras.\n\n"
                        + "Abre este enlace para elegir una nueva:\n" + link + "\n\n"
                        + "Vence en " + props.auth().resetPasswordTtl().toMinutes() + " minutos. "
                        + "Si no fuiste tú, ignora este correo; tu contraseña no cambia.", link);
    }

    void send(String to, String subject, String body, String link) {
        JavaMailSender mail = sender.getIfAvailable();
        if (!props.mail().enabled() || mail == null) {
            log.info("[correo simulado] para={} asunto=\"{}\" enlace={}", to, subject, link);
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(props.mail().from());
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(body);
            mail.send(msg);
        } catch (RuntimeException e) {
            log.error("No se pudo enviar el correo a {}: {}", to, e.getMessage());
        }
    }
}
