package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import ar.edu.utn.frvm.typeit.bemol_api.auth.config.PasswordRecoveryProperties;
import ar.edu.utn.frvm.typeit.bemol_api.auth.events.InstitutionalPasswordRecoveryRequested;
import ar.edu.utn.frvm.typeit.bemol_api.common.mail.MailMessage;
import ar.edu.utn.frvm.typeit.bemol_api.common.mail.MailMessages;
import ar.edu.utn.frvm.typeit.bemol_api.common.mail.MailProperties;
import ar.edu.utn.frvm.typeit.bemol_api.common.mail.MailSender;
import ar.edu.utn.frvm.typeit.bemol_api.common.mail.MailType;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

@Service
@RequiredArgsConstructor
@NullMarked
public class InstitutionalPasswordRecoveryMailService {

  private static final String TEMPLATE_NAME = "mail/institutional-password-recovery";

  private final MailSender mailSender;
  private final MailProperties mailProperties;
  private final PasswordRecoveryProperties passwordRecoveryProperties;
  private final SpringTemplateEngine templateEngine;
  private final FrontendAccessUrls accessUrls;

  public void send(final InstitutionalPasswordRecoveryRequested event) {
    final Context context = new Context(Locale.forLanguageTag("es"));
    context.setVariable(
        "resetUrl",
        accessUrls.tokenUrl(
            event.publicSubdomain(), "/auth/password-recovery/reset", event.token()));
    context.setVariable("logoUrl", accessUrls.institutionLogoUrl());
    context.setVariable("institutionName", event.institutionName());
    context.setVariable("fullName", event.fullName());
    context.setVariable(
        "expirationMinutes", passwordRecoveryProperties.tokenExpiration().toMinutes());

    mailSender.send(
        new MailMessage(
            mailProperties.from(),
            event.recipientEmail(),
            MailMessages.PASSWORD_RECOVERY_SUBJECT,
            templateEngine.process(TEMPLATE_NAME, context),
            MailType.PASSWORD_RECOVERY,
            event.auditContext()));
  }
}
