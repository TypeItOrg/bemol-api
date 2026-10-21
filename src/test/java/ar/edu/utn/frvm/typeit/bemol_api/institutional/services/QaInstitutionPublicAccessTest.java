package ar.edu.utn.frvm.typeit.bemol_api.institutional.services;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.*;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.InstitutionRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.validation.PublicSubdomainPolicy;
import ar.edu.utn.frvm.typeit.bemol_api.support.InstitutionalTestData;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;

class QaInstitutionPublicAccessTest {
  static Institution institution() {
    final var country = InstitutionalTestData.country("TST");
    final var province = InstitutionalTestData.province(country, "14");
    final var city = InstitutionalTestData.city(province, "14098");
    return Institution.builder()
        .id(UUID.randomUUID())
        .city(city)
        .name("Conservatorio QA")
        .slug("legacy-slug")
        .build();
  }

  @Test
  void I01_domainInvariant_optionalIndependentLabel() {
    final var institution = institution();
    institution.changePublicSubdomain("cboero");
    institution.changeSlug("different-legacy-slug");
    assertThat(institution.getPublicSubdomain()).isEqualTo("cboero");
    institution.changePublicSubdomain(null);
    assertThat(institution.getPublicSubdomain()).isNull();
    assertThat(institution.getSlug()).isEqualTo("different-legacy-slug");
    PublicSubdomainPolicy.validate("a");
    PublicSubdomainPolicy.validate("a".repeat(63));
    PublicSubdomainPolicy.validate("music-2026");
    assertThatThrownBy(() -> PublicSubdomainPolicy.validate("testing"))
        .isInstanceOf(InvalidPublicSubdomainException.class);
  }

  @ParameterizedTest(name = "[I01.domain-invariant] invalid label {index}: {0}")
  @ValueSource(
      strings = {
        "",
        "UPPER",
        "two.labels",
        "under_score",
        "-start",
        "end-",
        "white space",
        "www",
        "api",
        "admin",
        "auth",
        "test",
        "qa",
        "staging",
        "localhost"
      })
  void I01_domainInvariant_invalidAndReserved(final String label) {
    assertThatThrownBy(() -> PublicSubdomainPolicy.validate(label))
        .isInstanceOf(InvalidPublicSubdomainException.class);
  }

  @Test
  void I01_domainInvariant_rejectsOver63() {
    assertThatThrownBy(() -> PublicSubdomainPolicy.validate("a".repeat(64)))
        .isInstanceOf(InvalidPublicSubdomainException.class);
  }

  @Test
  void I01_postgresConstraints_translateOnlyNamedConflict() {
    final var repository = mock(InstitutionRepository.class);
    final var institution = institution();
    when(repository.findByIdForUpdate(institution.getId())).thenReturn(Optional.of(institution));
    when(repository.saveAndFlush(institution))
        .thenThrow(
            new org.springframework.dao.DataIntegrityViolationException(
                "duplicate",
                new org.hibernate.exception.ConstraintViolationException(
                    "unique",
                    new java.sql.SQLException("duplicate"),
                    "institutions_public_subdomain_unique")));
    assertThatThrownBy(
            () ->
                new UpdateInstitutionPublicAccessUseCase(repository, mock(AuditEventRecorder.class))
                    .execute(institution.getId(), "cboero"))
        .isInstanceOf(PublicSubdomainAlreadyExistsException.class);
    doThrow(
            new org.springframework.dao.DataIntegrityViolationException(
                "different constraint",
                new org.hibernate.exception.ConstraintViolationException(
                    "other", new java.sql.SQLException("other"), "institutions_slug_unique")))
        .when(repository)
        .saveAndFlush(institution);
    assertThatThrownBy(
            () ->
                new UpdateInstitutionPublicAccessUseCase(repository, mock(AuditEventRecorder.class))
                    .execute(institution.getId(), "different"))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }

  @Test
  void I02_minimalResolution_onlyActiveConfigured() {
    final var repository = mock(InstitutionRepository.class);
    final var institution = institution();
    institution.changePublicSubdomain("cboero");
    when(repository.findByPublicSubdomainAndActiveTrue("cboero"))
        .thenReturn(Optional.of(institution));
    final var response = new ResolveInstitutionPublicAccessUseCase(repository).execute("cboero");
    assertThat(response.id()).isEqualTo(institution.getId());
    assertThat(response.publicSubdomain()).isEqualTo("cboero");
    assertThat(response.logoUrl()).isNull();
    assertThat(response.getClass().getRecordComponents())
        .extracting(component -> component.getName())
        .containsExactly("id", "name", "publicSubdomain", "logoUrl");
  }

  @Test
  void I02_unknownInactive_notFound() {
    final var repository = mock(InstitutionRepository.class);
    when(repository.findByPublicSubdomainAndActiveTrue("absent")).thenReturn(Optional.empty());
    final var resolver = new ResolveInstitutionPublicAccessUseCase(repository);
    assertThatThrownBy(() -> resolver.execute("absent"))
        .isInstanceOf(InstitutionNotFoundException.class);
    assertThatThrownBy(() -> resolver.execute("UPPER"))
        .isInstanceOf(InstitutionNotFoundException.class);
  }

  @Test
  void I02_unavailable_distinguishedFromNotFound() {
    final var repository = mock(InstitutionRepository.class);
    when(repository.findByPublicSubdomainAndActiveTrue("cboero"))
        .thenThrow(new DataAccessResourceFailureException("offline"));
    assertThatThrownBy(
            () -> new ResolveInstitutionPublicAccessUseCase(repository).execute("cboero"))
        .isInstanceOf(InstitutionPublicAccessUnavailableException.class);
  }
}
