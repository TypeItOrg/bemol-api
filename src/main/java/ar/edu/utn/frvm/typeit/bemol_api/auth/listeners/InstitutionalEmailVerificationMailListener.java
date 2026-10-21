package ar.edu.utn.frvm.typeit.bemol_api.auth.listeners;

import ar.edu.utn.frvm.typeit.bemol_api.auth.events.InstitutionalEmailVerificationRequested;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.InstitutionalEmailVerificationMailService;
import ar.edu.utn.frvm.typeit.bemol_api.common.mail.MailSendingException;
import ar.edu.utn.frvm.typeit.bemol_api.config.AsyncConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
@NullMarked
public class InstitutionalEmailVerificationMailListener {

  private final InstitutionalEmailVerificationMailService mailService;

  @Async(AsyncConfig.MAIL_EXECUTOR)
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void on(final InstitutionalEmailVerificationRequested event) {
    try {
      mailService.send(event);
      log.info("[Auth] Email verification message sent, userId: {}", event.userId());
    } catch (final MailSendingException exception) {
      log.warn("[Auth] Email verification message could not be sent, userId: {}", event.userId());
    }
  }
}
