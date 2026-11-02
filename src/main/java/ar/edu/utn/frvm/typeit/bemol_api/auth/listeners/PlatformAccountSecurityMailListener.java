package ar.edu.utn.frvm.typeit.bemol_api.auth.listeners;

import ar.edu.utn.frvm.typeit.bemol_api.auth.events.PlatformAccountSecurityMessageRequested;
import ar.edu.utn.frvm.typeit.bemol_api.auth.events.PlatformAccountSecurityMessageRequested.Kind;
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
public class PlatformAccountSecurityMailListener {
  private final MailSender mail;
  private final MailProperties properties;
  private final FrontendAccessUrls urls;
  private final SpringTemplateEngine templates;

  @Async(AsyncConfig.MAIL_EXECUTOR)
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void on(final PlatformAccountSecurityMessageRequested event) {
    try {
      final String subject =
          switch (event.kind()) {
            case EMAIL_CONFIRMATION -> MailMessages.ACCOUNT_EMAIL_CHANGE_SUBJECT;
            case EMAIL_CHANGE_REQUESTED -> MailMessages.ACCOUNT_EMAIL_CHANGE_REQUESTED_SUBJECT;
            case EMAIL_CHANGED -> MailMessages.ACCOUNT_EMAIL_CHANGED_SUBJECT;
            case PASSWORD_CHANGED -> MailMessages.PASSWORD_CHANGED_SUBJECT;
          };
      final String description =
          switch (event.kind()) {
            case EMAIL_CONFIRMATION ->
                "Confirmá esta dirección para usarla en tu cuenta de administración de Bemol. Ingresá con tu cuenta y correo actuales para confirmar.";
            case EMAIL_CHANGE_REQUESTED ->
                "Se solicitó cambiar el correo de tu cuenta de administración de Bemol. Tu dirección actual se mantiene hasta confirmar la nueva.";
            case EMAIL_CHANGED ->
                "Se actualizó el correo de tu cuenta de administración de Bemol. Se cerraron las sesiones en todos tus dispositivos.";
            case PASSWORD_CHANGED ->
                "Se cambió la contraseña de tu cuenta de administración de Bemol. Se cerraron las sesiones en todos tus dispositivos.";
          };
      final var context = new Context(Locale.forLanguageTag("es"));
      context.setVariable("logoUrl", urls.brandLogoUrl());
      context.setVariable("logoAlt", "Bemol");
      context.setVariable("fullName", event.fullName());
      context.setVariable("institutionName", "Bemol");
      context.setVariable("messageTitle", subject);
      context.setVariable("messageDescription", description);
      context.setVariable(
          "messageFooter",
          event.kind() == Kind.EMAIL_CONFIRMATION
              ? "Si no pediste este cambio, no confirmes el enlace."
              : "Si no reconocés este cambio, contactá al operador de la plataforma.");
      final String token = event.token();
      context.setVariable("confirmation", token != null);
      context.setVariable(
          "verificationUrl",
          token == null ? "" : urls.platformTokenUrl("/account/profile/email/confirm", token));
      mail.send(
          new MailMessage(
              properties.from(),
              event.email(),
              subject,
              templates.process("mail/account-email-change", context),
              event.kind() == Kind.PASSWORD_CHANGED
                  ? MailType.PASSWORD_CHANGE
                  : MailType.ACCOUNT_EMAIL_CHANGE,
              event.auditContext()));
    } catch (final RuntimeException exception) {
      log.warn(
          "[Auth] Platform account notification could not be sent, platformAccountId: {}, failureType: {}",
          event.platformAccountId(),
          exception.getClass().getSimpleName());
    }
  }
}
