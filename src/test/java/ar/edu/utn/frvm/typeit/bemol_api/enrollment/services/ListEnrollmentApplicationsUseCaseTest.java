package ar.edu.utn.frvm.typeit.bemol_api.enrollment.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.PermissionAccess;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.ScopedAuthorizationService;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentApplication;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentMessages;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions.EnrollmentValidationException;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces.EnrollmentApplicationRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class ListEnrollmentApplicationsUseCaseTest {

  private static final UUID INSTITUTION_ID =
      UUID.fromString("00000000-0000-4000-8000-000000000001");

  @Mock private EnrollmentApplicationRepository repository;
  @Mock private EnrollmentApplicationResponseFactory responseFactory;
  @Mock private ScopedAuthorizationService authorization;
  @Mock private EnrollmentDocumentAudit audit;
  @InjectMocks private ListEnrollmentApplicationsUseCase useCase;

  @ParameterizedTest
  @ValueSource(
      strings = {
        "attachments.originalFileName",
        "attachments.reviewStatus",
        "attachments.observation",
        "applicantPerson.user.password"
      })
  void rejectsPrivateSortPathsBeforeEitherListingReadsData(final String property) {
    final var pageable = PageRequest.of(0, 20, Sort.by("createdAt").and(Sort.by(property)));

    assertThatThrownBy(
            () -> useCase.execute(INSTITUTION_ID, null, null, false, false, null, pageable))
        .isInstanceOf(EnrollmentValidationException.class)
        .hasMessage(EnrollmentMessages.APPLICATION_SORT_INVALID);
    assertThatThrownBy(
            () -> useCase.executeForPlatform(null, null, null, false, false, null, pageable))
        .isInstanceOf(EnrollmentValidationException.class)
        .hasMessage(EnrollmentMessages.APPLICATION_SORT_INVALID);

    verifyNoInteractions(repository, responseFactory, authorization, audit);
  }

  @Test
  void preservesAllowedInstitutionalSortAndPaginationWithoutDocumentAccess() {
    final var pageable =
        PageRequest.of(2, 10, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id")));
    given(authorization.managementAccess(PermissionCode.ENROLLMENT_ATTACHMENT_READ))
        .willReturn(PermissionAccess.none());
    given(repository.findByInstitutionId(INSTITUTION_ID, null, null, false, false, null, pageable))
        .willReturn(Page.<EnrollmentApplication>empty(pageable));

    final var page = useCase.execute(INSTITUTION_ID, null, null, false, false, null, pageable);

    assertThat(page.getPageable()).isEqualTo(pageable);
    verify(repository)
        .findByInstitutionId(INSTITUTION_ID, null, null, false, false, null, pageable);
    verifyNoInteractions(responseFactory, audit);
  }

  @Test
  void preservesAllowedPlatformSortAndPagination() {
    final var pageable = PageRequest.of(1, 10, Sort.by("applicantPerson.lastName"));
    given(authorization.managementAccess(PermissionCode.ENROLLMENT_ATTACHMENT_READ))
        .willReturn(PermissionAccess.institution());
    given(repository.findByFilters(null, null, null, false, false, null, pageable))
        .willReturn(Page.<EnrollmentApplication>empty(pageable));

    final var page = useCase.executeForPlatform(null, null, null, false, false, null, pageable);

    assertThat(page.getPageable()).isEqualTo(pageable);
    verify(repository).findByFilters(null, null, null, false, false, null, pageable);
    verifyNoInteractions(responseFactory, audit);
  }
}
