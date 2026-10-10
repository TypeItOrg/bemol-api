package ar.edu.utn.frvm.typeit.boero_api.institutional.services;

import ar.edu.utn.frvm.typeit.boero_api.authorization.interfaces.PersonRoleAssignmentRepository;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.GuardianLinkStatus;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.PersonGuardian;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.PersonGuardianRepository;
import ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.guardian.GuardianDependentResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ListGuardianDependentsUseCase {

  // Ended links are history; the tutor still sees what is pending or was rejected.
  private static final List<GuardianLinkStatus> VISIBLE_STATUSES =
      List.of(GuardianLinkStatus.PENDING, GuardianLinkStatus.ACTIVE, GuardianLinkStatus.REJECTED);

  private final PersonGuardianRepository personGuardianRepository;
  private final PersonRoleAssignmentRepository personRoleAssignmentRepository;

  @Transactional(readOnly = true)
  public List<GuardianDependentResponse> execute(
      final UUID institutionId, final UUID tutorPersonId) {
    final List<PersonGuardian> links =
        personGuardianRepository
            .findByInstitution_IdAndTutorPerson_IdAndStatusInOrderByCreatedAtAsc(
                institutionId, tutorPersonId, VISIBLE_STATUSES);

    if (links.isEmpty()) {
      return List.of();
    }

    final List<UUID> dependentIds =
        links.stream().map(link -> link.getDependentPerson().getId()).toList();
    final Map<UUID, Long> counts =
        personGuardianRepository
            .countActiveApplicationsByApplicant(institutionId, dependentIds)
            .stream()
            .collect(Collectors.toMap(count -> count.personId(), count -> count.total()));

    final Map<UUID, List<String>> roles =
        personRoleAssignmentRepository
            .findByPerson_IdInAndInstitution_Id(dependentIds, institutionId)
            .stream()
            .collect(
                Collectors.groupingBy(
                    assignment -> assignment.getPerson().getId(),
                    Collectors.mapping(
                        assignment -> assignment.getRole().getName(), Collectors.toList())));

    return links.stream()
        .map(
            link -> {
              final UUID dependentId = link.getDependentPerson().getId();

              return GuardianDependentResponse.from(
                  link,
                  counts.getOrDefault(dependentId, 0L),
                  roles.getOrDefault(dependentId, List.of()));
            })
        .toList();
  }
}
