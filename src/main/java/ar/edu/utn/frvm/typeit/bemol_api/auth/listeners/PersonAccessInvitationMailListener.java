package ar.edu.utn.frvm.typeit.bemol_api.auth.listeners;

import ar.edu.utn.frvm.typeit.bemol_api.auth.events.PersonAccessInvited;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.FrontendAccessUrls;
import ar.edu.utn.frvm.typeit.bemol_api.common.mail.MailMessage;
import ar.edu.utn.frvm.typeit.bemol_api.common.mail.MailMessages;
import ar.edu.utn.frvm.typeit.bemol_api.common.mail.MailProperties;
import ar.edu.utn.frvm.typeit.bemol_api.common.mail.MailSender;
import ar.edu.utn.frvm.typeit.bemol_api.common.mail.MailSendingException;
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
public class PersonAccessInvitationMailListener {
  private final MailSender mail;
  private final MailProperties properties;
  private final FrontendAccessUrls urls;
  private final SpringTemplateEngine templates;

  @Async(AsyncConfig.MAIL_EXECUTOR)
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void on(final PersonAccessInvited event) {
    final var context = new Context(Locale.forLanguageTag("es"));
    context.setVariable("logoUrl", urls.institutionLogoUrl());
    context.setVariable("expirationHours", 24);
    context.setVariable("fullName", event.fullName());
    context.setVariable("institutionName", event.institutionName());
    context.setVariable(
        "activationUrl",
        urls.tokenUrl(event.publicSubdomain(), "/auth/activate-account", event.token()));
    try {
      mail.send(
          new MailMessage(
              properties.from(),
              event.email(),
              MailMessages.PERSON_ACCESS_INVITATION_SUBJECT,
              templates.process("mail/person-access-invitation", context),
              MailType.PERSON_ACCESS_INVITATION,
              event.auditContext()));
    } catch (final MailSendingException exception) {
      // The default async handler would log the token-bearing event and SMTP diagnostics.
      log.warn("[Auth] Access invitation could not be sent, personId: {}", event.personId());
    }
  }
}
