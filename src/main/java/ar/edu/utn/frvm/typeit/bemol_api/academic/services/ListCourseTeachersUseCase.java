package ar.edu.utn.frvm.typeit.bemol_api.academic.services;

import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.TeacherOptionResponse;
import ar.edu.utn.frvm.typeit.bemol_api.academic.validation.AcademicNameNormalizer;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.RoleScope;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.SystemRoleCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.interfaces.RoleRepository;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.PaginatedResponse;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.PersonRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ListCourseTeachersUseCase {
  private final RoleRepository roleRepository;
  private final PersonRepository personRepository;

  @Transactional(readOnly = true)
  public PaginatedResponse<TeacherOptionResponse> execute(
      final UUID institutionId, final @Nullable String search, final Pageable pageable) {
    final var localRole =
        roleRepository.findByScopeAndCodeAndInstitution_Id(
            RoleScope.INSTITUTION, SystemRoleCode.TEACHER.name(), institutionId);
    if (localRole.isEmpty()) {
      return PaginatedResponse.from(Page.empty());
    }
    return PaginatedResponse.from(
        personRepository
            .search(
                institutionId,
                AcademicNameNormalizer.search(search),
                localRole.orElseThrow().getId(),
                pageable)
            .map(TeacherOptionResponse::from));
  }
}
