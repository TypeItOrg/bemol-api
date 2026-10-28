package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.PersonAccessInvitation;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.User;
import ar.edu.utn.frvm.typeit.bemol_api.auth.enums.EmailVerificationStatus;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.PersonAccessInvitationRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.UserRepository;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.interfaces.PersonRoleAssignmentRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.GuardianLinkStatus;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.PersonGuardian;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.InstitutionRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.PersonGuardianRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class PersonOwnAccessServiceTest {
  private static final Instant NOW = Instant.parse("2026-10-17T12:00:00Z");
  private final Institution institution = Institution.builder().id(UUID.randomUUID()).build();

  @Mock private UserRepository users;
  @Mock private PersonRoleAssignmentRepository roles;
  @Mock private PersonAccessInvitationRepository invitations;
  @Mock private Clock clock;
  @Mock private InstitutionRepository institutions;
  @Mock private ApplicationEventPublisher events;
  @Mock private PersonGuardianRepository links;
  @Mock private AuditEventRecorder audit;
  @Mock private AccountAccessAuthorization access;
  @Mock private AccountActionRateLimiter limiter;
  @InjectMocks private PersonOwnAccessProvisioning provisioning;
  private PersonOwnAccessService service;

  @BeforeEach
  void setUp() {
    service =
        new PersonOwnAccessService(
            institutions,
            links,
            users,
            invitations,
            clock,
            access,
            audit,
            provisioning,
            org.mockito.Mockito.mock(InstitutionalAccountManagementUseCase.class));
  }

  @Test
  void emptyBatchDoesNotQueryRepositories() {
    assertThat(service.statuses(institution.getId(), List.of())).isEmpty();
    verifyNoInteractions(users, roles, invitations, clock);
  }

  @Test
  void inactiveLinkDoesNotQueryRepositories() {
    final var link = PersonGuardian.builder().status(GuardianLinkStatus.PENDING).build();

    assertThat(service.status(link).status()).isEqualTo("UNAVAILABLE");
    verifyNoInteractions(users, roles, invitations, clock);
  }

  @Test
  void accountStatesUseOneBatchAndKeepTheirPrecedence() {
    final var blocked = user(false, false, EmailVerificationStatus.PENDING);
    final var disabled = user(true, false, EmailVerificationStatus.PENDING);
    final var unverified = user(true, true, EmailVerificationStatus.PENDING);
    final var enabled = user(true, true, EmailVerificationStatus.VERIFIED);
    final var accounts = List.of(blocked, disabled, unverified, enabled);
    final var personIds = accounts.stream().map(user -> user.getPerson().getId()).toList();
    when(users.findByPerson_IdInAndInstitution_Id(personIds, institution.getId()))
        .thenReturn(accounts);

    assertThat(service.statuses(institution.getId(), personIds))
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(
                blocked.getPerson().getId(), "INSTITUTION_BLOCKED",
                disabled.getPerson().getId(), "DISABLED",
                unverified.getPerson().getId(), "INVITATION_PENDING",
                enabled.getPerson().getId(), "ENABLED"));
    verify(users).findByPerson_IdInAndInstitution_Id(personIds, institution.getId());
    verifyNoMoreInteractions(users);
    verifyNoInteractions(invitations, clock);
  }

  @Test
  void mixedBatchQueriesOnlyPeopleWithoutAccountsAndKeepsInvitationRules() {
    final var account = user(true, true, EmailVerificationStatus.VERIFIED);
    final var noInvitation = person();
    final var represented = person();
    final var endedRepresentation = person();
    final var privilegedRepresented = person();
    final var privilegedInstitutional = person();
    final var withoutAccount =
        List.of(
            noInvitation.getId(),
            represented.getId(),
            endedRepresentation.getId(),
            privilegedRepresented.getId(),
            privilegedInstitutional.getId());
    final var allIds = new java.util.ArrayList<>(withoutAccount);
    allIds.add(account.getPerson().getId());
    when(users.findByPerson_IdInAndInstitution_Id(allIds, institution.getId()))
        .thenReturn(List.of(account));
    when(roles.findPersonIdsRequiringInstitutionalAccess(
            eq(allIds), eq(institution.getId()), anySet(), anySet()))
        .thenReturn(List.of(privilegedRepresented.getId(), privilegedInstitutional.getId()));
    when(clock.instant()).thenReturn(NOW);
    when(invitations.findPendingByPersonIds(withoutAccount, institution.getId(), NOW))
        .thenReturn(
            List.of(
                invitation(represented, GuardianLinkStatus.ACTIVE),
                invitation(endedRepresentation, GuardianLinkStatus.ENDED),
                invitation(privilegedRepresented, GuardianLinkStatus.ACTIVE),
                PersonAccessInvitation.issueInstitutionally(
                    privilegedInstitutional,
                    person(),
                    "person@example.test",
                    "hash",
                    NOW.plusSeconds(60))));

    assertThat(service.statuses(institution.getId(), allIds))
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(
                account.getPerson().getId(), "ENABLED",
                noInvitation.getId(), "NO_ACCOUNT",
                represented.getId(), "INVITATION_PENDING",
                endedRepresentation.getId(), "NO_ACCOUNT",
                privilegedRepresented.getId(), "INSTITUTION_REQUIRED",
                privilegedInstitutional.getId(), "INSTITUTION_REQUIRED"));
    verify(users).findByPerson_IdInAndInstitution_Id(allIds, institution.getId());
    verify(roles)
        .findPersonIdsRequiringInstitutionalAccess(
            eq(allIds), eq(institution.getId()), anySet(), anySet());
    verify(invitations).findPendingByPersonIds(withoutAccount, institution.getId(), NOW);
    verify(clock).instant();
    verifyNoMoreInteractions(users, roles, invitations, clock);
  }

  private Person person() {
    return Person.builder().id(UUID.randomUUID()).institution(institution).build();
  }

  private User user(
      boolean enabled, boolean selfAccessEnabled, EmailVerificationStatus verification) {
    return User.builder()
        .person(person())
        .institution(institution)
        .enabled(enabled)
        .selfAccessEnabled(selfAccessEnabled)
        .emailVerificationStatus(verification)
        .build();
  }

  private PersonAccessInvitation invitation(Person person, GuardianLinkStatus status) {
    final var representation =
        PersonGuardian.builder()
            .institution(institution)
            .dependentPerson(person)
            .status(status)
            .build();
    return PersonAccessInvitation.issue(
        representation, "person@example.test", "hash", NOW.plusSeconds(60));
  }
}
