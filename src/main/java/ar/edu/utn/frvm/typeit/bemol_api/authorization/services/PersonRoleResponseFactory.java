package ar.edu.utn.frvm.typeit.bemol_api.authorization.services;

import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.TrainingPathRepository;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.entities.PersonRoleAssignment;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.payloads.PersonRoleResponse;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PersonRoleResponseFactory {
  private final TrainingPathRepository paths;

  public PersonRoleResponse from(PersonRoleAssignment assignment) {
    var base = PersonRoleResponse.from(assignment);
    var names =
        paths.findAllById(assignment.getTrainingPathIds()).stream()
            .collect(Collectors.toMap(p -> p.getId(), p -> p.getName()));
    return new PersonRoleResponse(
        base.roleId(),
        base.technicalCode(),
        base.displayName(),
        base.assignedAt(),
        base.accessScope(),
        base.trainingPathIds(),
        names);
  }
}
