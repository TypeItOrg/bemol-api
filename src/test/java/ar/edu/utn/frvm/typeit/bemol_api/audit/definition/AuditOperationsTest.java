package ar.edu.utn.frvm.typeit.bemol_api.audit.definition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.utn.frvm.typeit.bemol_api.academic.audit.AcademicAuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.audit.exceptions.AuditValidationException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.audit.AuthAuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.audit.AuthorizationAuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.common.mail.MailAuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.audit.EnrollmentAuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.audit.InstitutionalAuditAction;
import java.util.List;
import org.junit.jupiter.api.Test;

class AuditOperationsTest {
  private static final AuditView PLATFORM_ACTIVITY = AuditView.platform(AuditFeed.ACTIVITY);

  @Test
  void assemblesLocalDefinitionsAndKeepsCodesAndLabelsAvailableWithoutEvents() {
    final var operations =
        new AuditOperations(
            List.of(
                new AcademicAuditAction.Provider(),
                new AuthAuditAction.Provider(),
                new AuthorizationAuditAction.Provider(),
                new MailAuditAction.Provider(),
                new EnrollmentAuditAction.Provider(),
                new InstitutionalAuditAction.Provider()));

    assertThat(operations.find("INSTRUMENT_UPDATED"))
        .isSameAs(AcademicAuditAction.INSTRUMENT_UPDATED);
    assertThat(operations.find("PERSON_CREATED")).isSameAs(InstitutionalAuditAction.PERSON_CREATED);
    assertThat(operations.modules(PLATFORM_ACTIVITY))
        .contains(new AuditModuleDefinition("COURSES", "Cursos"))
        .extracting(entry -> entry.code())
        .doesNotContain("EMAILS");
    assertThat(operations.feedOf("EMAILS")).isEqualTo(AuditFeed.EMAIL);
    assertThat(operations.resourceLabel("PERSON")).isEqualTo("Persona");
    assertThat(operations.resourceLabel("ENROLLMENT_APPLICATION")).isEqualTo("Inscripción");
    assertThatThrownBy(() -> operations.validateResource("UNKNOWN"))
        .isInstanceOf(AuditValidationException.class);
    assertThatCode(
            () ->
                operations.validate(AuditView.platform(AuditFeed.EMAIL), "EMAIL_FAILED", "EMAILS"))
        .doesNotThrowAnyException();
    assertThatThrownBy(() -> operations.validate(PLATFORM_ACTIVITY, "EMAIL_FAILED", null))
        .isInstanceOf(AuditValidationException.class);
    assertThatThrownBy(() -> operations.validate(PLATFORM_ACTIVITY, "UNKNOWN", null))
        .isInstanceOf(AuditValidationException.class);
    assertThatThrownBy(() -> operations.validate(PLATFORM_ACTIVITY, null, "UNKNOWN"))
        .isInstanceOf(AuditValidationException.class);
    assertThatCode(() -> operations.validate(PLATFORM_ACTIVITY, "PLATFORM_ACCESS_CHANGED", null))
        .doesNotThrowAnyException();
    assertThatThrownBy(
            () ->
                operations.validate(
                    AuditView.institution(AuditFeed.ACTIVITY), "PLATFORM_ACCESS_CHANGED", null))
        .isInstanceOf(AuditValidationException.class);
  }

  @Test
  void rejectsDuplicateOperationCodes() {
    final AuditOperationsProvider provider = () -> List.of(AcademicAuditAction.INSTRUMENT_CREATED);
    assertThatThrownBy(() -> new AuditOperations(List.of(provider, provider)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("INSTRUMENT_CREATED");
  }

  @Test
  void rejectsInconsistentModuleLabels() {
    final AuditOperationsProvider provider =
        () ->
            List.of(
                operation("ONE", "MODULE", "Nombre"), operation("TWO", "MODULE", "Otro nombre"));
    assertThatThrownBy(() -> new AuditOperations(List.of(provider)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("MODULE");
  }

  private static AuditOperation operation(
      final String code, final String module, final String label) {
    return new AuditOperation() {
      @Override
      public String name() {
        return code;
      }

      @Override
      public String getLabel() {
        return code;
      }

      @Override
      public AuditModuleDefinition getModule() {
        return new AuditModuleDefinition(module, label);
      }
    };
  }
}
