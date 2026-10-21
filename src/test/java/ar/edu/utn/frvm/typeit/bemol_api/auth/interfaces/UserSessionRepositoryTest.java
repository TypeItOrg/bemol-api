package ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.UserSession;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.bemol_api.support.InstitutionalTestData;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;

@DataJpaTest
class UserSessionRepositoryTest {

  @Autowired private UserSessionRepository userSessionRepository;
  @Autowired private EntityManager entityManager;

  @Test
  @DisplayName("Should find active sessions for user")
  void shouldFindActiveSessionsForUser() {
    Institution institution =
        InstitutionalTestData.createInstitution(entityManager, "sessions-one");
    UUID userId = createUserId(institution, "10000001");
    UserSession active = userSession(userId, true);
    UserSession inactive = userSession(userId, false);
    UserSession otherUser = userSession(createUserId(institution, "10000002"), true);
    userSessionRepository.save(active);
    userSessionRepository.save(inactive);
    userSessionRepository.saveAndFlush(otherUser);

    assertThat(userSessionRepository.findByUserIdAndActive(userId, true)).containsExactly(active);
  }

  @Test
  @DisplayName("Should page active sessions and count them")
  void shouldPageActiveSessionsAndCountThem() {
    Institution institution =
        InstitutionalTestData.createInstitution(entityManager, "sessions-two");
    UUID userId = createUserId(institution, "20000001");
    UserSession first = userSession(userId, true);
    UserSession second = userSession(userId, true);
    userSessionRepository.save(first);
    userSessionRepository.save(second);
    userSessionRepository.saveAndFlush(userSession(userId, false));

    var page = userSessionRepository.findByUserIdAndActive(userId, true, PageRequest.of(0, 10));

    assertThat(page.getContent()).containsExactlyInAnyOrder(first, second);
    assertThat(userSessionRepository.countByUserIdAndActive(userId, true)).isEqualTo(2);
  }

  @Test
  @DisplayName("Should find session only for owning user")
  void shouldFindSessionOnlyForOwningUser() {
    Institution institution =
        InstitutionalTestData.createInstitution(entityManager, "sessions-three");
    UUID userId = createUserId(institution, "30000001");
    UserSession session = userSessionRepository.saveAndFlush(userSession(userId, true));

    assertThat(userSessionRepository.findByIdAndUserId(session.getId(), userId)).contains(session);
    assertThat(userSessionRepository.findByIdAndUserId(session.getId(), UUID.randomUUID()))
        .isEmpty();
  }

  private static UserSession userSession(UUID userId, boolean active) {
    return UserSession.builder()
        .userId(userId)
        .ipAddress("192.0.2.10")
        .userAgent("Mozilla/5.0")
        .active(active)
        .build();
  }

  private UUID createUserId(final Institution institution, final String documentNumber) {
    return InstitutionalTestData.createUser(entityManager, institution, documentNumber).getId();
  }
}
