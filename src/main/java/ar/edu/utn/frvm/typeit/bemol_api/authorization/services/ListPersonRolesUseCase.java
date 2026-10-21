package ar.edu.utn.frvm.typeit.bemol_api.authorization.services;

import ar.edu.utn.frvm.typeit.bemol_api.authorization.interfaces.PersonRoleAssignmentRepository;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.payloads.PersonRoleResponse;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ListPersonRolesUseCase {
  private final PersonRoleResponseFactory responseFactory;

  private final InstitutionPersonResolver institutionPersonResolver;
  private final PersonRoleAssignmentRepository personRoleAssignmentRepository;

  @Transactional(readOnly = true)
  public List<PersonRoleResponse> execute(UUID institutionId, UUID personId) {
    institutionPersonResolver.requirePersonInInstitution(institutionId, personId);

    return personRoleAssignmentRepository
        .findByPerson_IdAndInstitution_Id(personId, institutionId)
        .stream()
        .map(responseFactory::from)
        .toList();
  }
}
