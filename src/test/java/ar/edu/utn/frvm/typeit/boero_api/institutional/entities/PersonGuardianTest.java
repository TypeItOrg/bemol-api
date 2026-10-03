package ar.edu.utn.frvm.typeit.boero_api.institutional.entities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.GuardianLinkAlreadyResolvedException;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PersonGuardianTest {

  private final Institution institution = Institution.builder().id(UUID.randomUUID()).build();
  private final Person tutor = Person.builder().id(UUID.randomUUID()).build();
  private final Person dependent = Person.builder().id(UUID.randomUUID()).build();
  private final Person reviewer = Person.builder().id(UUID.randomUUID()).build();

  private PersonGuardian pendingLink() {
    return PersonGuardian.request(institution, tutor, dependent, GuardianRelationship.MOTHER, true);
  }

  @Test
  @DisplayName("Should be born pending and unresolved")
  void request_startsPending() {
    final PersonGuardian link = pendingLink();

    assertThat(link.getStatus()).isEqualTo(GuardianLinkStatus.PENDING);
    assertThat(link.getRelationship()).isEqualTo(GuardianRelationship.MOTHER);
    assertThat(link.isPrimaryContact()).isTrue();
    assertThat(link.getResolvedBy()).isNull();
    assertThat(link.getResolvedAt()).isNull();
  }

  @Test
  @DisplayName("Should activate a pending link and record who resolved it")
  void approve_activatesPendingLink() {
    final PersonGuardian link = pendingLink();

    link.approve(reviewer);

    assertThat(link.getStatus()).isEqualTo(GuardianLinkStatus.ACTIVE);
    assertThat(link.getResolvedBy()).isSameAs(reviewer);
    assertThat(link.getResolvedAt()).isNotNull();
  }

  @Test
  @DisplayName("Should reject a pending link and record who resolved it")
  void reject_rejectsPendingLink() {
    final PersonGuardian link = pendingLink();

    link.reject(reviewer);

    assertThat(link.getStatus()).isEqualTo(GuardianLinkStatus.REJECTED);
    assertThat(link.getResolvedBy()).isSameAs(reviewer);
    assertThat(link.getResolvedAt()).isNotNull();
  }

  @Test
  @DisplayName("Should not resolve a link twice")
  void resolve_rejectsAlreadyResolvedLink() {
    final PersonGuardian approved = pendingLink();
    approved.approve(reviewer);
    final PersonGuardian rejected = pendingLink();
    rejected.reject(reviewer);

    assertThatThrownBy(() -> approved.reject(reviewer))
        .isInstanceOf(GuardianLinkAlreadyResolvedException.class);
    assertThatThrownBy(() -> approved.approve(reviewer))
        .isInstanceOf(GuardianLinkAlreadyResolvedException.class);
    assertThatThrownBy(() -> rejected.approve(reviewer))
        .isInstanceOf(GuardianLinkAlreadyResolvedException.class);
  }

  @Test
  @DisplayName("Should end an active link")
  void end_endsActiveLink() {
    final PersonGuardian link = pendingLink();
    link.approve(reviewer);

    link.end();

    assertThat(link.getStatus()).isEqualTo(GuardianLinkStatus.ENDED);
  }

  @Test
  @DisplayName("Should cancel a pending link")
  void end_endsPendingLink() {
    final PersonGuardian link = pendingLink();

    link.end();

    assertThat(link.getStatus()).isEqualTo(GuardianLinkStatus.ENDED);
  }

  @Test
  @DisplayName("Should only end pending or active links")
  void end_rejectsResolvedLink() {
    final PersonGuardian link = pendingLink();
    link.reject(reviewer);

    assertThatThrownBy(link::end).isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("Should only grant representation while active")
  void isActive_onlyWhenActive() {
    final PersonGuardian link = pendingLink();
    assertThat(link.isActive()).isFalse();

    link.approve(reviewer);
    assertThat(link.isActive()).isTrue();

    link.end();
    assertThat(link.isActive()).isFalse();
  }
}
