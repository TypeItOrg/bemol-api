package ar.edu.utn.frvm.typeit.bemol_api.common.exceptions;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.DocumentDefinition;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentForm;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentFormAssignment;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentFormVersion;
import java.sql.SQLException;
import java.util.UUID;
import java.util.stream.Stream;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

class EnrollmentFormErrorTranslationTest {
  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @ParameterizedTest
  @CsvSource({
    "enrollment_form_versions_draft_unique,409",
    "enrollment_form_assignments_context_unique,409",
    "enrollment_form_versions_immutable_check,409",
    "enrollment_form_versions_assigned_check,409",
    "enrollment_application_form_immutable_check,409",
    "enrollment_application_form_context_check,409",
    "enrollment_application_answers_frozen_check,409",
    "enrollment_forms_name_check,400",
    "enrollment_form_versions_definition_check,400",
    "enrollment_application_form_version_tenant_fk,400",
    "enrollment_application_base_answers_check,400",
    "enrollment_pdf_settings_title_check,400"
  })
  void mapsNamedConstraintsWithoutChangingPdfValidation(final String name, final int status) {
    final var exception =
        new DataIntegrityViolationException(
            "Persistence rejected",
            new ConstraintViolationException("Rejected", new SQLException("Rejected"), name));

    assertThat(handler.handlePersistenceException(exception).getStatusCode().value())
        .isEqualTo(status);
  }

  @ParameterizedTest
  @MethodSource("revisionEntities")
  void translatesKnownOptimisticConflicts(final Class<?> entity) {
    final var exception = new ObjectOptimisticLockingFailureException(entity, UUID.randomUUID());

    assertThat(handler.handleDocumentRevisionConflict(exception).getStatusCode().value())
        .isEqualTo(409);
  }

  static Stream<Class<?>> revisionEntities() {
    return Stream.of(
        EnrollmentForm.class,
        EnrollmentFormVersion.class,
        EnrollmentFormAssignment.class,
        DocumentDefinition.class);
  }
}
