package ar.edu.utn.frvm.typeit.bemol_api.institutional.services;

import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.PersonAccessInvitationRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.UserRepository;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.entities.PersonRoleAssignment;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.SystemRoleCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.interfaces.PersonRoleAssignmentRepository;
import ar.edu.utn.frvm.typeit.bemol_api.common.search.SearchNormalization;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.PaginatedResponse;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.enums.PersonStatus;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.PersonRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.person.PersonSummaryResponse;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.person.PlatformPersonSummaryResponse;
import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ListPlatformPeopleUseCase {

  private static final String INSTITUTION_NAME_SORT = "institutionName";
  private static final String INSTITUTION_ENTITY_NAME_SORT = "institution.name";

  private final PersonRepository personRepository;
  private final UserRepository userRepository;
  private final PersonAccessInvitationRepository invitations;
  private final Clock clock;
  private final PersonRoleAssignmentRepository personRoleAssignmentRepository;

  @Transactional(readOnly = true)
  public PaginatedResponse<PlatformPersonSummaryResponse> execute(
      final @Nullable String search,
      final @Nullable UUID institutionId,
      final @Nullable SystemRoleCode roleCode,
      final @Nullable PersonStatus status,
      final Pageable pageable) {
    final var now = clock.instant();
    final String normalizedSearch = SearchNormalization.normalizeSearch(search);
    final Pageable repositoryPageable = mapInstitutionSort(pageable);
    final Page<Person> peoplePage =
        personRepository.findPlatformPeople(
            normalizedSearch,
            institutionId,
            roleCode == null ? null : roleCode.name(),
            status == null ? null : status.name(),
            now,
            repositoryPageable);

    if (peoplePage.isEmpty()) {
      return PaginatedResponse.from(peoplePage.map(this::withoutRoles));
    }

    final List<UUID> personIds =
        peoplePage.getContent().stream().map(mappedPerson -> mappedPerson.getId()).toList();
    final Map<UUID, Boolean> accessByPerson =
        userRepository.findByPerson_IdIn(personIds).stream()
            .collect(Collectors.toMap(user -> user.getPerson().getId(), user -> user.isEnabled()));
    final var pendingInvitations =
        new HashSet<>(invitations.findPendingInstitutionalPersonIds(personIds, now));
    final Map<UUID, List<PersonSummaryResponse.PersonRoleSummaryResponse>> rolesByPerson =
        personRoleAssignmentRepository.findByPerson_IdIn(personIds).stream()
            .collect(
                Collectors.groupingBy(
                    assignment -> assignment.getPerson().getId(),
                    Collectors.mapping(this::toRoleResponse, Collectors.toList())));

    return PaginatedResponse.from(
        peoplePage.map(
            person ->
                PlatformPersonSummaryResponse.from(
                    person,
                    rolesByPerson.getOrDefault(person.getId(), List.of()),
                    accessByPerson.getOrDefault(person.getId(), false),
                    accessByPerson.containsKey(person.getId()),
                    !accessByPerson.containsKey(person.getId())
                        && pendingInvitations.contains(person.getId()))));
  }

  private Pageable mapInstitutionSort(final Pageable pageable) {
    final List<Sort.Order> mappedOrders =
        pageable.getSort().stream()
            .map(
                order ->
                    order.withProperty(
                        INSTITUTION_NAME_SORT.equals(order.getProperty())
                            ? INSTITUTION_ENTITY_NAME_SORT
                            : order.getProperty()))
            .toList();
    final Sort mappedSort = Sort.by(mappedOrders);
    return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), mappedSort);
  }

  private PlatformPersonSummaryResponse withoutRoles(final Person person) {
    return PlatformPersonSummaryResponse.from(person, List.of(), false, false, false);
  }

  private PersonSummaryResponse.PersonRoleSummaryResponse toRoleResponse(
      final PersonRoleAssignment assignment) {
    return new PersonSummaryResponse.PersonRoleSummaryResponse(
        assignment.getRole().getCode(), assignment.getRole().getName());
  }
}
