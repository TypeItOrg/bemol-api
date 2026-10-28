package ar.edu.utn.frvm.typeit.bemol_api.auth.listeners;

import ar.edu.utn.frvm.typeit.bemol_api.auth.events.AccountEmailMessageRequested;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.FrontendAccessUrls;
import ar.edu.utn.frvm.typeit.bemol_api.common.mail.MailMessage;
import ar.edu.utn.frvm.typeit.bemol_api.common.mail.MailMessages;
import ar.edu.utn.frvm.typeit.bemol_api.common.mail.MailProperties;
import ar.edu.utn.frvm.typeit.bemol_api.common.mail.MailSender;
import ar.edu.utn.frvm.typeit.bemol_api.common.mail.MailType;
import ar.edu.utn.frvm.typeit.bemol_api.config.AsyncConfig;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

@Component
@RequiredArgsConstructor
@Slf4j
public class AccountEmailChangeMailListener {
  private final MailSender mail;
  private final MailProperties properties;
  private final FrontendAccessUrls urls;
  private final SpringTemplateEngine templates;

  @Async(AsyncConfig.MAIL_EXECUTOR)
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void on(final AccountEmailMessageRequested event) {
    try {
      final var context = new Context(Locale.forLanguageTag("es"));
      final String token = event.token();
      context.setVariable(
          "logoUrl",
          urls.logoUrl(
              event.publicSubdomain(), event.auditContext().institutionId(), event.logoVersion()));
      context.setVariable(
          "logoAlt", event.logoVersion() != null ? "Logo de " + event.institutionName() : "Bemol");
      context.setVariable("fullName", event.fullName());
      context.setVariable("institutionName", event.institutionName());
      context.setVariable("confirmation", token != null);
      context.setVariable(
          "verificationUrl",
          token == null
              ? ""
              : urls.tokenUrl(event.publicSubdomain(), "/auth/confirm-account-email", token));
      mail.send(
          new MailMessage(
              properties.from(),
              event.email(),
              token == null
                  ? MailMessages.ACCOUNT_EMAIL_CHANGED_SUBJECT
                  : MailMessages.ACCOUNT_EMAIL_CHANGE_SUBJECT,
              templates.process("mail/account-email-change", context),
              MailType.ACCOUNT_EMAIL_CHANGE,
              event.auditContext()));
    } catch (final RuntimeException exception) {
      log.warn(
          "[Auth] Account email notification could not be sent, personId: {}, failureType: {}",
          event.personId(),
          exception.getClass().getSimpleName());
    }
  }
}
