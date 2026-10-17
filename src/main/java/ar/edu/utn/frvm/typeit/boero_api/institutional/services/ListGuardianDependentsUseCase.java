package ar.edu.utn.frvm.typeit.boero_api.institutional.services;

import static java.util.Objects.requireNonNull;

import ar.edu.utn.frvm.typeit.boero_api.auth.services.PersonOwnAccessService;
import ar.edu.utn.frvm.typeit.boero_api.authorization.interfaces.PersonRoleAssignmentRepository;
import ar.edu.utn.frvm.typeit.boero_api.common.web.PaginatedResponse;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.GuardianLinkStatus;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.GuardianRelationship;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.PersonGuardian;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.PersonGuardianRepository;
import ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.guardian.GuardianDependentResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ListGuardianDependentsUseCase {

  // Ended links are history; the tutor still sees what is pending or was rejected.
  private static final List<GuardianLinkStatus> VISIBLE_STATUSES =
      List.of(GuardianLinkStatus.PENDING, GuardianLinkStatus.ACTIVE, GuardianLinkStatus.REJECTED);

  private final PersonGuardianRepository personGuardianRepository;
  private final PersonOwnAccessService ownAccess;
  private final PersonRoleAssignmentRepository personRoleAssignmentRepository;

  @Transactional(readOnly = true)
  public List<GuardianDependentResponse> execute(
      final UUID institutionId, final UUID tutorPersonId) {
    final List<PersonGuardian> links =
        personGuardianRepository
            .findByInstitution_IdAndTutorPerson_IdAndStatusInOrderByCreatedAtAsc(
                institutionId, tutorPersonId, VISIBLE_STATUSES);

    return responses(institutionId, links);
  }

  @Transactional(readOnly = true)
  public PaginatedResponse<GuardianDependentResponse> page(
      final UUID institutionId,
      final UUID tutorPersonId,
      final int page,
      final int size,
      final String search,
      final @Nullable GuardianLinkStatus status,
      final @Nullable GuardianRelationship relationship,
      final @Nullable Boolean primaryContact,
      final String sortField,
      final String sortDirection) {
    final Page<PersonGuardian> links =
        personGuardianRepository.findAll(
            GuardianDependentQuery.matching(
                institutionId,
                tutorPersonId,
                search,
                status,
                relationship,
                primaryContact,
                sortField,
                sortDirection),
            PageRequest.of(page, size));

    return PaginatedResponse.from(
        new PageImpl<>(
            responses(institutionId, links.getContent()),
            links.getPageable(),
            links.getTotalElements()));
  }

  private List<GuardianDependentResponse> responses(
      final UUID institutionId, final List<PersonGuardian> links) {
    if (links.isEmpty()) {
      return List.of();
    }

    final List<UUID> dependentIds =
        links.stream().map(link -> link.getDependentPerson().getId()).toList();
    final Map<UUID, String> ownAccessStatuses =
        ownAccess.statuses(
            institutionId,
            links.stream()
                .filter(PersonGuardian::isActive)
                .map(link -> link.getDependentPerson().getId())
                .distinct()
                .toList());
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
                  roles.getOrDefault(dependentId, List.of()),
                  link.isActive()
                      ? requireNonNull(ownAccessStatuses.get(dependentId))
                      : "UNAVAILABLE");
            })
        .toList();
  }
}
