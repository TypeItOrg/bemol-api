package ar.edu.utn.frvm.typeit.boero_api.institutional.services;

import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.PersonGuardian;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.DependentApplicationCount;
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

  private final PersonGuardianRepository personGuardianRepository;

  @Transactional(readOnly = true)
  public List<GuardianDependentResponse> execute(
      final UUID institutionId, final UUID tutorPersonId) {
    final List<PersonGuardian> links =
        personGuardianRepository.findByInstitution_IdAndTutorPerson_IdOrderByCreatedAtAsc(
            institutionId, tutorPersonId);

    if (links.isEmpty()) {
      return List.of();
    }

    final List<UUID> dependentIds =
        links.stream().map(link -> link.getDependentPerson().getId()).toList();
    final Map<UUID, Long> counts =
        personGuardianRepository
            .countActiveApplicationsByApplicant(institutionId, dependentIds)
            .stream()
            .collect(
                Collectors.toMap(
                    DependentApplicationCount::personId, DependentApplicationCount::total));

    return links.stream()
        .map(
            link ->
                GuardianDependentResponse.from(
                    link, counts.getOrDefault(link.getDependentPerson().getId(), 0L)))
        .toList();
  }
}
