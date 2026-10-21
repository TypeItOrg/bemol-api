package ar.edu.utn.frvm.typeit.bemol_api.auth.listeners;

import ar.edu.utn.frvm.typeit.bemol_api.auth.events.InstitutionalPasswordRecoveryRequested;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.InstitutionalPasswordRecoveryMailService;
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
public class InstitutionalPasswordRecoveryMailListener {

  private final InstitutionalPasswordRecoveryMailService mailService;

  @Async(AsyncConfig.MAIL_EXECUTOR)
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void on(final InstitutionalPasswordRecoveryRequested event) {
    try {
      mailService.send(event);
      log.info("[Auth] Password recovery email sent, userId: {}", event.userId());
    } catch (final MailSendingException exception) {
      log.warn("[Auth] Password recovery email could not be sent, userId: {}", event.userId());
    }
  }
}
