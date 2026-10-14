package ar.edu.utn.frvm.typeit.boero_api.institutional.services;

import static ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.InstitutionPublicAccessResponse.logoUrl;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import ar.edu.utn.frvm.typeit.boero_api.auth.services.SessionRevocationService;
import ar.edu.utn.frvm.typeit.boero_api.common.storage.StorageService;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.PublicSubdomainAlreadyExistsException;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.InstitutionRepository;
import ar.edu.utn.frvm.typeit.boero_api.institutional.payloads.requests.*;
import ar.edu.utn.frvm.typeit.boero_api.support.*;
import ar.edu.utn.frvm.typeit.boero_api.support.PostgresTestDatabase;
import jakarta.persistence.EntityManager;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.InputStreamSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.*;

@DataJpaTest
@Import({
  JpaAuditingTestConfig.class,
  UpdateInstitutionPublicAccessUseCase.class,
  ResolveInstitutionPublicAccessUseCase.class,
  InstitutionLogoUseCase.class,
  UpdateInstitutionUseCase.class,
  UpdateInstitutionalInstitutionUseCase.class
})
@Testcontainers
@IntegrationTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class QaInstitutionPublicAccessPostgresIntegrationTest {
  static final PostgresTestDatabase POSTGRES =
      new PostgresTestDatabase("postgres:17-alpine", "institution_public_access");

  @DynamicPropertySource
  static void database(final DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    registry.add("spring.flyway.enabled", () -> true);
    registry.add("spring.flyway.sql-migration-prefix", () -> "");
    registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
  }

  @Autowired EntityManager em;
  @Autowired JdbcTemplate jdbc;
  @Autowired InstitutionRepository institutions;
  @Autowired PlatformTransactionManager transactions;
  @Autowired UpdateInstitutionPublicAccessUseCase publicAccess;
  @Autowired ResolveInstitutionPublicAccessUseCase resolve;
  @Autowired InstitutionLogoUseCase logos;
  @Autowired UpdateInstitutionUseCase platformMetadata;
  @Autowired UpdateInstitutionalInstitutionUseCase tenantMetadata;
  @MockitoBean StorageService storage;
  @MockitoBean SessionRevocationService revocations;
  Map<String, byte[]> objects;

  <T> T tx(final Supplier<T> action) {
    return new TransactionTemplate(transactions).execute(status -> action.get());
  }

  record Fixture(UUID id, UUID cityId) {}

  Fixture institution() {
    return tx(
        () -> {
          final var institution =
              InstitutionalTestData.createInstitution(em, "qa-" + UUID.randomUUID());
          em.flush();
          return new Fixture(institution.getId(), institution.getCity().getId());
        });
  }

  Institution read(final UUID id) {
    return tx(() -> institutions.findById(id).orElseThrow());
  }

  @BeforeEach
  void storage() {
    objects = new ConcurrentHashMap<>();
    lenient()
        .doAnswer(
            invocation -> {
              final InputStreamSource source = invocation.getArgument(3);
              try (final var stream = source.getInputStream()) {
                objects.put(invocation.getArgument(0), stream.readAllBytes());
              }
              return null;
            })
        .when(storage)
        .write(anyString(), anyString(), anyLong(), any());
    lenient()
        .when(storage.deletePhysicalFile(anyString()))
        .thenAnswer(invocation -> objects.remove(invocation.getArgument(0)) != null);
    lenient()
        .when(storage.loadAsInputStream(anyString()))
        .thenAnswer(
            invocation -> new java.io.ByteArrayInputStream(objects.get(invocation.getArgument(0))));
  }

  @Test
  void I01_postgresConstraints_uniqueIncludesInactiveAndAllowsNull() {
    final var a = institution();
    final var b = institution();
    publicAccess.execute(a.id(), "reserved-owner");
    tx(
        () -> {
          final var institution = institutions.findById(a.id()).orElseThrow();
          institution.updateStatus(false);
          institutions.saveAndFlush(institution);
          return null;
        });
    assertThatThrownBy(() -> publicAccess.execute(b.id(), "reserved-owner"))
        .isInstanceOf(PublicSubdomainAlreadyExistsException.class);
    assertThatThrownBy(
            () ->
                tx(
                    () ->
                        jdbc.update(
                            "UPDATE institutions SET public_subdomain=? WHERE institution_id=?",
                            "reserved-owner",
                            b.id())))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("institutions_public_subdomain_unique");
    publicAccess.execute(a.id(), null);
    publicAccess.execute(b.id(), null);
    assertThat(read(a.id()).getPublicSubdomain()).isNull();
    assertThat(read(b.id()).getPublicSubdomain()).isNull();
  }

  @Test
  void I01_postgresConstraints_dnsReservedAndCompleteLogoMetadata() {
    final var a = institution();
    for (final String name : List.of("UPPER", "a.b", "_bad", "-bad", "bad-", "a".repeat(64))) {
      assertThatThrownBy(
              () ->
                  tx(
                      () ->
                          jdbc.update(
                              "UPDATE institutions SET public_subdomain=? WHERE institution_id=?",
                              name,
                              a.id())))
          .isInstanceOf(DataIntegrityViolationException.class);
    }
    for (final String name : List.of("api", "testing")) {
      assertThatThrownBy(
              () ->
                  tx(
                      () ->
                          jdbc.update(
                              "UPDATE institutions SET public_subdomain=? WHERE institution_id=?",
                              name,
                              a.id())))
          .isInstanceOf(DataIntegrityViolationException.class)
          .hasMessageContaining("institutions_public_subdomain_reserved_check");
    }
    assertThatThrownBy(
            () ->
                tx(
                    () ->
                        jdbc.update(
                            "UPDATE institutions SET logo_key='key',logo_version='version',logo_size=1 WHERE institution_id=?",
                            a.id())))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("institutions_logo_metadata_check");
  }

  @Test
  void I01_legacyUpdate_bothMetadataPutsPreservePublicAccessAndLogo() throws Exception {
    final var a = institution();
    publicAccess.execute(a.id(), "legacy-preserved");
    final String url = logos.replace(a.id(), QaInstitutionLogoTest.png()).logoUrl();
    platformMetadata.execute(
        a.id(),
        new UpdateInstitutionRequest(
            "New platform name",
            "different-slug",
            a.cityId(),
            null,
            null,
            null,
            null,
            null,
            null,
            true));
    tenantMetadata.execute(
        a.id(),
        new UpdateInstitutionalInstitutionRequest(
            "New tenant name", a.cityId(), null, null, null, null, null, null));
    assertThat(read(a.id()).getPublicSubdomain()).isEqualTo("legacy-preserved");
    assertThat(logoUrl(read(a.id()))).isEqualTo(url);
    assertThat(objects).hasSize(1);
  }

  @Test
  @Timeout(20)
  void I01_postgresConstraints_concurrentReservationHasOneOwnerAndApplicationConflict()
      throws Exception {
    final var a = institution();
    final var b = institution();
    final var start = new CountDownLatch(1);
    try (final var workers = Executors.newFixedThreadPool(2)) {
      final List<Future<Boolean>> results = new ArrayList<>();
      for (final var fixture : List.of(a, b)) {
        results.add(
            workers.submit(
                () -> {
                  start.await();
                  try {
                    publicAccess.execute(fixture.id(), "concurrent-owner");
                    return true;
                  } catch (PublicSubdomainAlreadyExistsException conflict) {
                    return false;
                  }
                }));
      }
      start.countDown();
      assertThat(
              List.of(
                  results.getFirst().get(10, TimeUnit.SECONDS),
                  results.getLast().get(10, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(true, false);
      assertThat(
              jdbc.queryForObject(
                  "SELECT COUNT(*) FROM institutions WHERE public_subdomain='concurrent-owner'",
                  Long.class))
          .isEqualTo(1L);
    }
  }

  @Test
  @Timeout(20)
  void I01_legacyUpdate_concurrentMetadataCannotOverwriteBranding() throws Exception {
    final var fixture = institution();
    final var file = QaInstitutionLogoTest.png();
    try (final var workers = Executors.newSingleThreadExecutor()) {
      tx(
          () -> {
            final var stale = institutions.findById(fixture.id()).orElseThrow();
            try {
              workers
                  .submit(
                      () -> {
                        publicAccess.execute(fixture.id(), "concurrent-branding");
                        logos.replace(fixture.id(), file);
                      })
                  .get(10, TimeUnit.SECONDS);
            } catch (Exception exception) {
              throw new IllegalStateException(exception);
            }
            stale.rename("Concurrent legacy rename");
            institutions.saveAndFlush(stale);
            return null;
          });
    }
    assertThat(read(fixture.id()).getPublicSubdomain()).isEqualTo("concurrent-branding");
    assertThat(logoUrl(read(fixture.id()))).isNotNull();
    assertThat(objects).hasSize(1);
  }

  @Test
  void I02_unknownInactive_realRepositoryResolver() {
    final var a = institution();
    publicAccess.execute(a.id(), "known-active");
    assertThat(resolve.execute("known-active").id()).isEqualTo(a.id());
    tx(
        () -> {
          final var institution = institutions.findById(a.id()).orElseThrow();
          institution.updateStatus(false);
          institutions.saveAndFlush(institution);
          return null;
        });
    assertThatThrownBy(() -> resolve.execute("known-active"))
        .isInstanceOf(
            ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.InstitutionNotFoundException
                .class);
    assertThatThrownBy(() -> resolve.execute("not-configured"))
        .isInstanceOf(
            ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions.InstitutionNotFoundException
                .class);
  }

  @Test
  void L01_platformLogoLifecycle_pgCommitCurrentBytesAndDelete() throws Exception {
    final var a = institution();
    final String first = logos.replace(a.id(), QaInstitutionLogoTest.png()).logoUrl();
    final String second = logos.replace(a.id(), QaInstitutionLogoTest.png()).logoUrl();
    assertThat(second).isNotEqualTo(first);
    assertThat(objects).hasSize(1);
    assertThat(logos.get(a.id()).resource().getContentAsByteArray())
        .isEqualTo(QaInstitutionLogoTest.png().getBytes());
    logos.delete(a.id());
    assertThat(logoUrl(read(a.id()))).isNull();
    assertThat(objects).isEmpty();
    assertThatThrownBy(() -> logos.get(a.id()))
        .isInstanceOf(
            ar.edu.utn.frvm.typeit.boero_api.institutional.exceptions
                .InstitutionLogoNotFoundException.class);
  }

  @Test
  void L03_logoCacheFallback_inFlightBytesSurviveReplacementCleanup() throws Exception {
    final var fixture = institution();
    logos.replace(fixture.id(), QaInstitutionLogoTest.png());
    final var previous = logos.get(fixture.id());
    final var replacement =
        new org.springframework.mock.web.MockMultipartFile(
            "file", "logo.jpg", "image/jpeg", QaInstitutionLogoTest.image("jpeg", 3, 3));
    logos.replace(fixture.id(), replacement);
    assertThat(previous.resource().getContentAsByteArray())
        .isEqualTo(QaInstitutionLogoTest.png().getBytes());
    assertThat(logos.get(fixture.id()).resource().getContentAsByteArray())
        .isEqualTo(replacement.getBytes());
    assertThat(objects).hasSize(1);
  }

  @Test
  void L03_replacementFailure_pgRollbackKeepsOldAndCleansNew() throws Exception {
    final var a = institution();
    logos.replace(a.id(), QaInstitutionLogoTest.png());
    final var old = read(a.id());
    doAnswer(
            invocation -> {
              final String key = invocation.getArgument(0);
              objects.put(key, new byte[] {1});
              throw new IllegalStateException("simulated upload failure");
            })
        .when(storage)
        .write(anyString(), anyString(), anyLong(), any());
    assertThatThrownBy(() -> logos.replace(a.id(), QaInstitutionLogoTest.png()))
        .isInstanceOf(IllegalStateException.class);
    assertThat(read(a.id()).getLogoKey()).isEqualTo(old.getLogoKey());
    assertThat(logoUrl(read(a.id()))).isEqualTo(logoUrl(old));
    assertThat(objects.keySet()).containsExactly(old.getLogoKey());
  }

  @Test
  void L03_replacementFailure_pgOuterRollbackRestoresOldMetadata() throws Exception {
    final var a = institution();
    logos.replace(a.id(), QaInstitutionLogoTest.png());
    final var old = read(a.id());
    final var file = QaInstitutionLogoTest.png();
    assertThatThrownBy(
            () ->
                tx(
                    () -> {
                      logos.replace(a.id(), file);
                      throw new IllegalStateException("simulated commit rejection");
                    }))
        .isInstanceOf(IllegalStateException.class);
    assertThat(read(a.id()).getLogoKey()).isEqualTo(old.getLogoKey());
    assertThat(objects.keySet()).containsExactly(old.getLogoKey());
  }

  @Test
  @Timeout(20)
  void L03_replacementFailure_pgLockSerializesConcurrentReplacements() throws Exception {
    final var a = institution();
    logos.replace(a.id(), QaInstitutionLogoTest.png());
    final var firstWritten = new CountDownLatch(1);
    final var releaseFirst = new CountDownLatch(1);
    final var writes = new AtomicInteger();
    doAnswer(
            invocation -> {
              final InputStreamSource source = invocation.getArgument(3);
              try (final var stream = source.getInputStream()) {
                objects.put(invocation.getArgument(0), stream.readAllBytes());
              }
              if (writes.incrementAndGet() == 1) {
                firstWritten.countDown();
                if (!releaseFirst.await(10, TimeUnit.SECONDS)) {
                  throw new IllegalStateException("timeout");
                }
              }
              return null;
            })
        .when(storage)
        .write(anyString(), anyString(), anyLong(), any());
    try (final var workers = Executors.newFixedThreadPool(2)) {
      final var file = QaInstitutionLogoTest.png();
      final var first = workers.submit(() -> logos.replace(a.id(), file));
      assertThat(firstWritten.await(10, TimeUnit.SECONDS)).isTrue();
      final var second = workers.submit(() -> logos.replace(a.id(), file));
      releaseFirst.countDown();
      first.get(10, TimeUnit.SECONDS);
      final var last = second.get(10, TimeUnit.SECONDS);
      assertThat(logoUrl(read(a.id()))).isEqualTo(last.logoUrl());
      assertThat(objects).hasSize(1);
    } finally {
      releaseFirst.countDown();
    }
  }
}
