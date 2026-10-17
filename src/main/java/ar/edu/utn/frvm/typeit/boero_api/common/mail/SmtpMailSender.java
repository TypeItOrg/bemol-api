package ar.edu.utn.frvm.typeit.boero_api.common.mail;

import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditAction;
import ar.edu.utn.frvm.typeit.boero_api.audit.services.MailAuditRecorder;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SmtpMailSender implements MailSender {

  private final JavaMailSender javaMailSender;
  private final MailAuditRecorder audit;

  @Override
  public void send(final MailMessage mailMessage) {
    try {
      final MimeMessage message = javaMailSender.createMimeMessage();
      final MimeMessageHelper helper =
          new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
      helper.setFrom(mailMessage.from());
      helper.setTo(mailMessage.to());
      helper.setSubject(mailMessage.subject());
      helper.setText(mailMessage.htmlBody(), true);
      javaMailSender.send(message);
    } catch (MessagingException | MailException exception) {
      recordOutcome(mailMessage, AuditAction.EMAIL_FAILED);
      throw new MailSendingException(exception);
    }
    recordOutcome(mailMessage, AuditAction.EMAIL_SENT);
  }

  // shortcut: best-effort after SMTP; use durable delivery tracking if loss on crash becomes
  // unacceptable.
  private void recordOutcome(final MailMessage message, final AuditAction outcome) {
    try {
      audit.record(message.auditContext(), message.type(), message.to(), outcome);
    } catch (final RuntimeException exception) {
      // Do not turn a successful SMTP send into a retry or expose SMTP/persistence diagnostics.
      log.error(
          "[Audit] Mail outcome could not be recorded, action: {}, institutionId: {}, failureType: {}",
          outcome,
          message.auditContext().institutionId(),
          exception.getClass().getSimpleName());
    }
  }
}
