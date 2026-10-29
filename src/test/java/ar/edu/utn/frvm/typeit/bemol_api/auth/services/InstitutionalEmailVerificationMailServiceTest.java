package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import ar.edu.utn.frvm.typeit.bemol_api.auth.config.EmailVerificationProperties;
import ar.edu.utn.frvm.typeit.bemol_api.auth.config.FrontendPublicProperties;
import ar.edu.utn.frvm.typeit.bemol_api.auth.events.InstitutionalEmailVerificationRequested;
import ar.edu.utn.frvm.typeit.bemol_api.auth.listeners.InstitutionalEmailVerificationMailListener;
import ar.edu.utn.frvm.typeit.bemol_api.common.mail.MailAuditContext;
import ar.edu.utn.frvm.typeit.bemol_api.common.mail.MailMessage;
import ar.edu.utn.frvm.typeit.bemol_api.common.mail.MailProperties;
import ar.edu.utn.frvm.typeit.bemol_api.common.mail.MailSender;
import ar.edu.utn.frvm.typeit.bemol_api.common.mail.MailSendingException;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

@ExtendWith(MockitoExtension.class)
class InstitutionalEmailVerificationMailServiceTest {

  @Mock private MailSender mailSender;

  private InstitutionalEmailVerificationMailService mailService;

  @BeforeEach
  void setUp() {
    final ClassLoaderTemplateResolver templateResolver = new ClassLoaderTemplateResolver();
    templateResolver.setPrefix("templates/");
    templateResolver.setSuffix(".html");
    templateResolver.setTemplateMode(TemplateMode.HTML);
    templateResolver.setCharacterEncoding("UTF-8");
    templateResolver.setCacheable(false);

    final SpringTemplateEngine templateEngine = new SpringTemplateEngine();
    templateEngine.setTemplateResolver(templateResolver);
    mailService =
        new InstitutionalEmailVerificationMailService(
            mailSender,
            new MailProperties("no-reply@example.com"),
            new EmailVerificationProperties(
                "http://localhost:3000", Duration.ofHours(24), Duration.ofMinutes(1)),
            templateEngine,
            new FrontendAccessUrls(new FrontendPublicProperties("http://localhost:3000", "")));
  }

  @Test
  void listenerRendersVerificationTemplateAndDelegatesToMailSender() {
    final InstitutionalEmailVerificationRequested event =
        new InstitutionalEmailVerificationRequested(
            UUID.randomUUID(),
            "ana@example.com",
            "Conservatorio Superior de Música Felipe Boero",
            "Ana García",
            "token-123",
            MailAuditContext.capture(UUID.randomUUID(), UUID.randomUUID()));

    new InstitutionalEmailVerificationMailListener(mailService).on(event);

    final ArgumentCaptor<MailMessage> mailCaptor = ArgumentCaptor.forClass(MailMessage.class);
    verify(mailSender).send(mailCaptor.capture());

    final MailMessage message = mailCaptor.getValue();
    assertThat(message.from()).isEqualTo("no-reply@example.com");
    assertThat(message.to()).isEqualTo("ana@example.com");
    assertThat(message.subject()).isEqualTo("Confirmá tu correo electrónico");
    assertThat(message.htmlBody())
        .contains("http://localhost:3000/auth/email-verification/confirm?token=token-123")
        .contains("Bemol")
        .contains("http://localhost:3000/brand/logo-email.png")
        .contains("Manrope")
        .contains("#f5f3f0")
        .contains("#6f0926")
        .contains("Conservatorio Superior de Música Felipe Boero")
        .contains("Hola,")
        .contains("Ana García")
        .contains("24 horas")
        .doesNotContain("${resetUrl}", "th:href", "th:src", "th:text");
  }

  @Test
  void listenerRendersInstitutionLogoWhenInstitutionHasLogoVersion() {
    final UUID institutionId = UUID.randomUUID();
    final InstitutionalEmailVerificationRequested event =
        new InstitutionalEmailVerificationRequested(
            UUID.randomUUID(),
            "ana@example.com",
            "Conservatorio Superior de Música Felipe Boero",
            "Ana García",
            "token-123",
            "cboero",
            "logo-v1",
            MailAuditContext.capture(institutionId, UUID.randomUUID()));

    new InstitutionalEmailVerificationMailListener(mailService).on(event);

    final ArgumentCaptor<MailMessage> mailCaptor = ArgumentCaptor.forClass(MailMessage.class);
    verify(mailSender).send(mailCaptor.capture());

    assertThat(mailCaptor.getValue().htmlBody())
        .contains(
            "http://localhost:3000/api/public/institutions/"
                + institutionId
                + "/logo?v=logo-v1&amp;format=png")
        .contains("Logo de Conservatorio Superior de Música Felipe Boero")
        .doesNotContain("http://localhost:3000/brand/logo-email.png");
  }

  @Test
  void smtpFailureCannotEscapeTheListener() {
    doThrow(new MailSendingException(new IllegalStateException("SMTP unavailable")))
        .when(mailSender)
        .send(any());
    var event =
        new InstitutionalEmailVerificationRequested(
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            "ana@example.com",
            "Conservatorio",
            "Ana García",
            "token-123",
            MailAuditContext.capture(UUID.randomUUID(), UUID.randomUUID()));

    assertThatCode(() -> new InstitutionalEmailVerificationMailListener(mailService).on(event))
        .doesNotThrowAnyException();

    ArgumentCaptor<MailMessage> message = ArgumentCaptor.forClass(MailMessage.class);
    verify(mailSender).send(message.capture());
    assertThat(message.getValue().to()).isEqualTo("ana@example.com");
    assertThat(message.getValue().htmlBody()).contains("token=token-123");
  }
}
