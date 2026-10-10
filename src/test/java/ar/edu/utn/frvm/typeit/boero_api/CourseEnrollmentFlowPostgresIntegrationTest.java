package ar.edu.utn.frvm.typeit.boero_api;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.*;

import ar.edu.utn.frvm.typeit.boero_api.academic.entities.*;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.*;
import ar.edu.utn.frvm.typeit.boero_api.academic.exceptions.AcademicValidationException;
import ar.edu.utn.frvm.typeit.boero_api.academic.payloads.AcademicYearStatusRequest;
import ar.edu.utn.frvm.typeit.boero_api.academic.payloads.CourseClassDayRequest;
import ar.edu.utn.frvm.typeit.boero_api.academic.payloads.CourseClassRequest;
import ar.edu.utn.frvm.typeit.boero_api.academic.payloads.CourseClassScheduleRequest;
import ar.edu.utn.frvm.typeit.boero_api.academic.payloads.ReplaceCourseClassesRequest;
import ar.edu.utn.frvm.typeit.boero_api.academic.services.ReplaceCourseClassesUseCase;
import ar.edu.utn.frvm.typeit.boero_api.academic.services.UpdateAcademicYearStatusUseCase;
import ar.edu.utn.frvm.typeit.boero_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.boero_api.authorization.enums.SystemRoleCode;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.AssignPersonSystemRoleUseCase;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.InstitutionRoleProvisioner;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.*;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.*;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.enums.EducationLevel;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.EnrollmentPeriodClosedException;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.EnrollmentValidationException;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.*;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.services.*;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.*;
import ar.edu.utn.frvm.typeit.boero_api.support.InstitutionalTestData;
import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@AutoConfigureMockMvc(addFilters = false)
class CourseEnrollmentFlowPostgresIntegrationTest extends DatabaseMigrationTestSupport {
  @Autowired EntityManager em;
  @Autowired MockMvc http;
  @Autowired ReplaceCourseClassesUseCase replaceClasses;
  @Autowired PlatformTransactionManager transactions;
  @Autowired CourseEnrollmentService enrollments;
  @Autowired EnrollmentApplicationService applications;
  @Autowired ApproveEnrollmentApplicationUseCase approve;
  @Autowired RejectEnrollmentApplicationCourseUseCase reject;
  @Autowired ListCourseEnrollmentOptionsUseCase options;
  @Autowired ListEnrollmentApplicationCoursesUseCase catalog;
  @Autowired ListCourseWaitlistUseCase waitlist;
  @Autowired CourseIndividualSlotFactory slots;
  @Autowired InstitutionRoleProvisioner roles;
  @Autowired AssignPersonSystemRoleUseCase assignRole;
  @Autowired TeacherCourseService teaching;
  @Autowired UpdateAcademicYearStatusUseCase closeYear;

  @BeforeEach
  void authenticateManagementOperations() {
    authenticatePlatformAdministrator();
  }

  @Test
  void differentApplicantsCanRequestSameCourseAndCancellationReleasesRequest() {
    final var f = fixture(false);
    final var first = draft(f, "00001001");
    final var second = draft(f, "00001002");
    select(first, f.courseId());
    select(second, f.courseId());
    applications.cancelApplication(first.personId(), first.applicationId());
    assertThat(status(first)).isEqualTo("CANCELLED");
    assertThat(status(second)).isEqualTo("PENDING");
    final var next =
        tx(
            () -> {
              final var previous = em.find(EnrollmentApplication.class, first.applicationId());
              final var application =
                  persist(
                      EnrollmentApplication.createForTrainingPath(
                          previous.getInstitution(),
                          previous.getApplicantPerson(),
                          requireNonNull(previous.getTrainingPath()),
                          requireNonNull(previous.getAcademicYear()),
                          requireNonNull(previous.getEnrollmentPeriod())));
              return new Request(application.getId(), previous.getApplicantPerson().getId());
            });
    select(next, f.courseId());
    assertThat(status(next)).isEqualTo("PENDING");
  }

  @Test
  void parentApprovalCreatesNoEnrollmentAndCatalogIncludesCourseWithoutLevelOrInstrument() {
    final var f = fixture(false);
    final var request = submitted(f, "00002001");
    final var courseOptions =
        catalog.execute(
            request.personId(), request.applicationId(), null, null, null, PageRequest.of(0, 20));
    assertThat(courseOptions.getContent())
        .extracting(
            mappedEnrollmentCourseOptionResponse -> mappedEnrollmentCourseOptionResponse.courseId())
        .contains(f.courseId());
    assertThat(courseOptions.getContent().getFirst().hasCapacity()).isTrue();
    approve.execute(f.institutionId(), request.applicationId(), null);
    assertThat(
            enrollments
                .listOwn(f.institutionId(), request.personId(), null, null, PageRequest.of(0, 20))
                .items())
        .isEmpty();
    assertThat(status(request)).isEqualTo("PENDING");
  }

  @Test
  void catalogCapacityQueriesStayBoundedAcrossGroupAndIndividualCourses() {
    final var f = fixture(false);
    final var request = draft(f, "00012001");
    final var statistics =
        em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
    final boolean statisticsEnabled = statistics.isStatisticsEnabled();
    statistics.setStatisticsEnabled(true);
    try {
      statistics.clear();
      assertThat(
              catalog
                  .execute(
                      request.personId(),
                      request.applicationId(),
                      null,
                      null,
                      null,
                      PageRequest.of(0, 20))
                  .getContent())
          .hasSize(1)
          .allMatch(
              mappedEnrollmentCourseOptionResponse ->
                  mappedEnrollmentCourseOptionResponse.hasCapacity());
      final long singleCourseQueries = statistics.getPrepareStatementCount();

      final var individual = addCourse(f, true, "Individual", 2);
      addCourse(f, false, "Grupal adicional", 3);
      addCourse(f, true, "Otro individual", 4);
      statistics.clear();
      final var available =
          catalog.execute(
              request.personId(), request.applicationId(), null, null, null, PageRequest.of(0, 20));
      assertThat(available.getContent())
          .hasSize(4)
          .allMatch(
              mappedEnrollmentCourseOptionResponse ->
                  mappedEnrollmentCourseOptionResponse.hasCapacity());
      // A mixed page adds only the batch query for individual slots.
      assertThat(statistics.getPrepareStatementCount())
          .isLessThanOrEqualTo(singleCourseQueries + 1);

      enroll(f, approved(f, "00012002"));
      enroll(individual, approved(individual, "00012003"));
      final var remaining =
          catalog.execute(
              request.personId(), request.applicationId(), null, null, null, PageRequest.of(0, 20));
      assertThat(remaining.getContent())
          .filteredOn(value -> value.courseId().equals(f.courseId()))
          .allMatch(value -> !value.hasCapacity());
      assertThat(remaining.getContent())
          .filteredOn(value -> value.courseId().equals(individual.courseId()))
          .allMatch(
              mappedEnrollmentCourseOptionResponse ->
                  mappedEnrollmentCourseOptionResponse.hasCapacity());
    } finally {
      statistics.setStatisticsEnabled(statisticsEnabled);
    }
  }

  @Test
  void teacherClassPaginationIncludesMetadataForBothCourseFormats() {
    final var f = fixture(false);
    final var individual = addCourse(f, true, "Instrumental", 2);
    final var teacher =
        tx(
            () -> {
              final var institution = em.find(Institution.class, f.institutionId());
              final var person = persist(InstitutionalTestData.person(institution, "00013001"));
              assignRole.execute(person, SystemRoleCode.TEACHER, false);
              persist(
                  CourseClassTeacher.create(
                      institution, em.find(CourseClass.class, f.classId()), person));
              persist(
                  CourseClassTeacher.create(
                      institution, em.find(CourseClass.class, individual.classId()), person));
              return person.getId();
            });
    final var first = teaching.list(f.institutionId(), teacher, PageRequest.of(0, 1));
    final var second = teaching.list(f.institutionId(), teacher, PageRequest.of(1, 1));
    assertThat(first.totalItems()).isEqualTo(2);
    assertThat(second.totalItems()).isEqualTo(2);
    assertThat(first.items()).hasSize(1);
    assertThat(second.items()).hasSize(1);
    assertThat(List.of(first.items().getFirst(), second.items().getFirst()))
        .extracting(
            mappedTeacherCourseClassResponse ->
                mappedTeacherCourseClassResponse.academicSpaceName(),
            mappedTeacherCourseClassResponse -> mappedTeacherCourseClassResponse.instrumentName())
        .containsExactlyInAnyOrder(tuple("Curso", null), tuple("Instrumental", "Instrumental"));
  }

  @Test
  void lastGroupSeatIsAtomicAndWaitlistRequiresExplicitAcceptance() throws Exception {
    final var f = fixture(false);
    final var first = approved(f, "00003001");
    final var second = approved(f, "00003002");
    final var winners = race(f, first, second);
    assertThat(winners).hasSize(1);
    final var queued = status(first).equals("WAITLISTED") ? first : second;
    final var waiting = waitlist.execute(f.institutionId(), f.courseId());
    assertThat(waiting).hasSize(1);
    assertThat(waiting.getFirst().waitlistNumber()).isEqualTo(1);
    assertThat(waiting.getFirst().hasCapacity()).isFalse();
    assertThat(
            catalog
                .execute(
                    queued.personId(),
                    queued.applicationId(),
                    null,
                    null,
                    null,
                    PageRequest.of(0, 20))
                .getContent()
                .getFirst()
                .hasCapacity())
        .isFalse();
    enrollments.withdraw(
        f.institutionId(),
        winners.getFirst().id(),
        new WithdrawCourseEnrollmentRequest(
            CourseWithdrawalType.VOLUNTARY, "Baja", winners.getFirst().version()),
        null);
    assertThat(status(queued)).isEqualTo("WAITLISTED");
    assertThat(waitlist.execute(f.institutionId(), f.courseId()).getFirst().hasCapacity()).isTrue();
    final var accepted = enroll(f, queued);
    assertThat(accepted.status()).isEqualTo(CourseEnrollmentStatus.ENROLLED);
    assertThat(accepted.academicStatus()).isEqualTo(AcademicEnrollmentStatus.IN_PROGRESS);
    assertThat(waitlist.execute(f.institutionId(), f.courseId())).isEmpty();
  }

  @Test
  void individualSlotCannotBeAssignedTwiceAndOptionsAreReadOnly() throws Exception {
    final var f = fixture(true);
    final var first = approved(f, "00004001");
    final var second = approved(f, "00004002");
    final Long before = slotCount(f);
    assertThat(options.execute(f.institutionId(), f.courseId()).classes()).hasSize(1);
    assertThat(options.execute(f.institutionId(), f.courseId()).classes()).hasSize(1);
    assertThat(slotCount(f)).isEqualTo(before);
    assertThat(race(f, first, second)).hasSize(1);
    final var periods =
        options
            .execute(f.institutionId(), f.courseId())
            .classes()
            .getFirst()
            .days()
            .getFirst()
            .schedules()
            .getFirst()
            .individualSlots();
    assertThat(periods).filteredOn(slot -> !slot.available()).hasSize(1);
    assertThat(periods)
        .filteredOn(
            mappedCourseIndividualSlotOptionResponse ->
                mappedCourseIndividualSlotOptionResponse.available())
        .hasSize(1);
  }

  @Test
  void rejectedChildWrongParentAndWrongScheduleAreRejected() {
    final var f = fixture(false);
    final var first = approved(f, "00005001");
    final UUID childId = childId(first);
    final var request = assignment(f);
    assertThatThrownBy(
            () ->
                enrollments.enrollApplicationCourse(
                    f.institutionId(), UUID.randomUUID(), childId, request, null))
        .isInstanceOf(RuntimeException.class);
    assertThatThrownBy(
            () ->
                enrollments.enrollApplicationCourse(
                    f.institutionId(),
                    first.applicationId(),
                    childId,
                    new EnrollApplicationCourseRequest(
                        f.classId(),
                        List.of(new CourseScheduleAssignmentRequest(UUID.randomUUID(), null)),
                        null),
                    null))
        .isInstanceOf(EnrollmentValidationException.class);
    reject.execute(
        f.institutionId(),
        first.applicationId(),
        childId,
        new RejectEnrollmentApplicationCourseRequest("Rechazada", null),
        null);
    assertThatThrownBy(() -> enroll(f, first)).isInstanceOf(EnrollmentValidationException.class);
    assertThat(status(first)).isEqualTo("REJECTED");
  }

  @Test
  void teacherAndStudentQueriesArePersonalAndYearClosureReleasesAssignments() {
    final var f = fixture(false);
    final var request = approved(f, "00006001");
    final var enrollment = enroll(f, request);
    final var teachers =
        tx(
            () -> {
              final var institution = em.find(Institution.class, f.institutionId());
              final var owner = persist(InstitutionalTestData.person(institution, "00006002"));
              final var other = persist(InstitutionalTestData.person(institution, "00006003"));
              assignRole.execute(owner, SystemRoleCode.TEACHER, false);
              assignRole.execute(other, SystemRoleCode.TEACHER, false);
              persist(
                  CourseClassTeacher.create(
                      institution, em.find(CourseClass.class, f.classId()), owner));
              return List.of(owner.getId(), other.getId());
            });
    assertThat(teaching.list(f.institutionId(), teachers.getFirst(), PageRequest.of(0, 20)).items())
        .hasSize(1);
    assertThat(teaching.list(f.institutionId(), teachers.getLast(), PageRequest.of(0, 20)).items())
        .isEmpty();
    assertThat(
            teaching
                .listEnrollments(
                    f.institutionId(), teachers.getFirst(), f.classId(), PageRequest.of(0, 20))
                .items())
        .extracting(mappedCourseEnrollmentResponse -> mappedCourseEnrollmentResponse.id())
        .containsExactly(enrollment.id());
    assertThatThrownBy(
            () ->
                teaching.listEnrollments(
                    f.institutionId(), teachers.getLast(), f.classId(), PageRequest.of(0, 20)))
        .isInstanceOf(RuntimeException.class);
    assertThat(
            enrollments
                .listOwn(f.institutionId(), teachers.getLast(), null, null, PageRequest.of(0, 20))
                .items())
        .isEmpty();
    assertThat(
            enrollments
                .listOwn(UUID.randomUUID(), request.personId(), null, null, PageRequest.of(0, 20))
                .items())
        .isEmpty();
    closeYear.execute(
        f.institutionId(), f.yearId(), new AcademicYearStatusRequest(AcademicYearStatus.CLOSED));
    final var closed = enrollments.get(f.institutionId(), enrollment.id());
    assertThat(closed.status()).isEqualTo(CourseEnrollmentStatus.COMPLETED);
    assertThat(closed.academicStatus()).isEqualTo(AcademicEnrollmentStatus.PENDING_RESULT);
    assertThat(closed.schedules()).allMatch(value -> value.releasedAt() != null);
  }

  @Test
  void courseAndDatabaseConstraintsRejectForeignContextAndDuplicateAssignments() {
    final var f = fixture(true);
    final var foreign = fixture(false);
    final var request = draft(f, "00007001");
    assertThatThrownBy(() -> select(request, foreign.courseId()))
        .isInstanceOf(EnrollmentValidationException.class);
    final var enrollment = enroll(f, approved(f, "00007002"));
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "UPDATE course_enrollments SET course_class_id = ? WHERE course_enrollment_id = ?",
                    foreign.classId(),
                    enrollment.id()))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "UPDATE course_enrollments SET enrollment_application_course_id = NULL WHERE course_enrollment_id = ?",
                    enrollment.id()))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "UPDATE courses SET instrument_id = NULL WHERE course_id = ?", f.courseId()))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "UPDATE course_enrollment_schedules SET start_time = '09:00' WHERE course_enrollment_id = ?",
                    enrollment.id()))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                replaceClasses.execute(
                    f.institutionId(), f.courseId(), new ReplaceCourseClassesRequest(List.of())))
        .isInstanceOf(EnrollmentValidationException.class);
    assertThat(enrollments.get(f.institutionId(), enrollment.id()).schedules()).hasSize(1);
  }

  @Test
  void databaseRejectsSlotsOutsideScheduleOrMisalignedWithPeriod() {
    final var f = fixture(true);
    final var slotId =
        options
            .execute(f.institutionId(), f.courseId())
            .classes()
            .getFirst()
            .days()
            .getFirst()
            .schedules()
            .getFirst()
            .individualSlots()
            .getFirst()
            .id();
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "UPDATE course_individual_slots SET start_time = '11:00', end_time = '11:30' WHERE course_individual_slot_id = ?",
                    slotId))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("Individual slot does not match its schedule");
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "UPDATE course_individual_slots SET start_time = '10:15', end_time = '10:45' WHERE course_individual_slot_id = ?",
                    slotId))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("Individual slot does not match its schedule");
    assertThat(slotCount(f)).isEqualTo(2L);
  }

  @Test
  void closedPeriodBlocksSubmissionAndWrongYearCourseCannotBeSelected() {
    final var f = fixture(false);
    final var request = draft(f, "00008001");
    final var nextYearCourse =
        tx(
            () -> {
              final var institution = em.find(Institution.class, f.institutionId());
              final var year =
                  persist(
                      AcademicYear.create(
                          institution,
                          2027,
                          LocalDate.of(2027, 1, 1),
                          LocalDate.of(2027, 12, 31),
                          LocalDate.of(2027, 1, 1)));
              final var previous = em.find(Course.class, f.courseId());
              return persist(Course.create(institution, previous.getStudyPlanSpace(), year, null))
                  .getId();
            });
    assertThatThrownBy(() -> select(request, nextYearCourse))
        .isInstanceOf(EnrollmentValidationException.class);
    select(request, f.courseId());
    applications.updateDraft(
        request.personId(),
        request.applicationId(),
        new UpdateEnrollmentDraftRequest(
            EnrollmentDraftData.builder()
                .academicBackground(
                    AcademicBackgroundDto.builder()
                        .currentlyStudying(false)
                        .educationLevel(EducationLevel.SECONDARY)
                        .schoolOrigin("Colegio")
                        .secondaryCompleted(true)
                        .build())
                .preference(
                    PreferenceDto.builder()
                        .preferredShift("MORNING")
                        .allowsImageUse(false)
                        .isReenrolling(false)
                        .build())
                .build()));
    jdbcTemplate.update(
        "UPDATE enrollment_periods SET end_date = CURRENT_TIMESTAMP - interval '1 minute' WHERE enrollment_period_id = ?",
        f.periodId());
    assertThatThrownBy(
            () -> applications.submitApplication(request.personId(), request.applicationId()))
        .isInstanceOf(EnrollmentPeriodClosedException.class);
    assertThat(status(request)).isEqualTo("PENDING");
  }

  @Test
  void teacherHttpAccessDoesNotGrantAdministrationAndRejectsOtherInstitutions() throws Exception {
    final var f = fixture(false);
    final var teacher =
        tx(
            () -> {
              final var institution = em.find(Institution.class, f.institutionId());
              final var person = persist(InstitutionalTestData.person(institution, "00009001"));
              assignRole.execute(person, SystemRoleCode.TEACHER, false);
              persist(
                  CourseClassTeacher.create(
                      institution, em.find(CourseClass.class, f.classId()), person));
              return person.getId();
            });
    final var principal =
        new JwtAuthenticatedUser(
            UUID.randomUUID(), teacher, "00009001", f.institutionId(), UUID.randomUUID(), "test");
    final var authentication =
        new TestingAuthenticationToken(principal, "", "ROLE_INSTITUTIONAL_USER");
    SecurityContextHolder.getContext().setAuthentication(authentication);
    try {
      http.perform(
              MockMvcRequestBuilders.get(
                      "/api/v1/institutions/{id}/teacher/classes", f.institutionId())
                  .principal(authentication))
          .andExpect(MockMvcResultMatchers.status().isOk())
          .andExpect(MockMvcResultMatchers.jsonPath("$.items.length()").value(1));
      http.perform(
              MockMvcRequestBuilders.get(
                      "/api/v1/institutions/{id}/course-enrollments", f.institutionId())
                  .principal(authentication))
          .andExpect(MockMvcResultMatchers.status().isForbidden());
      http.perform(
              MockMvcRequestBuilders.get(
                      "/api/v1/institutions/{id}/teacher/classes", UUID.randomUUID())
                  .principal(authentication))
          .andExpect(MockMvcResultMatchers.status().isForbidden());
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  @Test
  void simultaneousParentApprovalsAssignDistinctWaitlistNumbersWithoutEnrolling() throws Exception {
    final var f = fixture(false);
    enroll(f, approved(f, "00010001"));
    final var first = submitted(f, "00010002");
    final var second = submitted(f, "00010003");
    final var start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      final var futures =
          List.of(first, second).stream()
              .map(
                  request ->
                      executor.submit(
                          () -> {
                            start.await();
                            authenticatePlatformAdministrator();
                            try {
                              return approve.execute(
                                  f.institutionId(), request.applicationId(), null);
                            } finally {
                              SecurityContextHolder.clearContext();
                            }
                          }))
              .toList();
      start.countDown();
      for (final var future : futures) {
        future.get(20, TimeUnit.SECONDS);
      }
    }
    assertThat(waitlist.execute(f.institutionId(), f.courseId()))
        .extracting(
            mappedCourseWaitlistEntryResponse -> mappedCourseWaitlistEntryResponse.waitlistNumber())
        .containsExactly(1, 2);
    assertThat(
            enrollments
                .listOwn(f.institutionId(), first.personId(), null, null, PageRequest.of(0, 20))
                .items())
        .isEmpty();
    assertThat(
            enrollments
                .listOwn(f.institutionId(), second.personId(), null, null, PageRequest.of(0, 20))
                .items())
        .isEmpty();
  }

  @Test
  void replacingUnassignedClassesRebuildsPrecalculatedSlotsWithoutForeignKeyFailures() {
    final var f = fixture(true);
    final var teacher =
        tx(
            () -> {
              final var person =
                  persist(
                      InstitutionalTestData.person(
                          em.find(Institution.class, f.institutionId()), "00011001"));
              assignRole.execute(person, SystemRoleCode.TEACHER, false);
              return person.getId();
            });
    assertThatThrownBy(
            () ->
                replaceClasses.execute(
                    f.institutionId(),
                    f.courseId(),
                    new ReplaceCourseClassesRequest(
                        List.of(
                            new CourseClassRequest(
                                List.of(teacher),
                                List.of(
                                    new CourseClassDayRequest(
                                        CourseDay.TUESDAY,
                                        null,
                                        30,
                                        List.of(
                                            new CourseClassScheduleRequest(
                                                LocalTime.of(23, 0),
                                                LocalTime.of(23, 30, 30))))))))))
        .isInstanceOf(AcademicValidationException.class);
    assertThat(options.execute(f.institutionId(), f.courseId()).classes())
        .extracting(
            mappedCourseEnrollmentClassOptionResponse ->
                mappedCourseEnrollmentClassOptionResponse.id())
        .containsExactly(f.classId());
    assertThat(slotCount(f)).isEqualTo(2L);

    final var result =
        replaceClasses.execute(
            f.institutionId(),
            f.courseId(),
            new ReplaceCourseClassesRequest(
                List.of(
                    new CourseClassRequest(
                        List.of(teacher),
                        List.of(
                            new CourseClassDayRequest(
                                CourseDay.TUESDAY,
                                null,
                                30,
                                List.of(
                                    new CourseClassScheduleRequest(
                                        LocalTime.of(14, 0), LocalTime.of(16, 0)))))))));
    assertThat(result.classes()).hasSize(1);
    assertThat(result.classes().getFirst().id()).isNotEqualTo(f.classId());
    assertThat(slotCount(f)).isEqualTo(4L);
    assertThat(
            options
                .execute(f.institutionId(), f.courseId())
                .classes()
                .getFirst()
                .days()
                .getFirst()
                .schedules()
                .getFirst()
                .individualSlots())
        .hasSize(4)
        .allMatch(
            mappedCourseIndividualSlotOptionResponse ->
                mappedCourseIndividualSlotOptionResponse.available());
  }

  private List<CourseEnrollmentResponse> race(Fixture f, Request first, Request second)
      throws Exception {
    final var start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      final var futures =
          List.of(first, second).stream()
              .map(
                  request ->
                      executor.submit(
                          () -> {
                            start.await();
                            authenticatePlatformAdministrator();
                            try {
                              return enroll(f, request);
                            } catch (EnrollmentValidationException expected) {
                              return null;
                            } finally {
                              SecurityContextHolder.clearContext();
                            }
                          }))
              .toList();
      start.countDown();
      final var winners = new ArrayList<CourseEnrollmentResponse>();
      for (var future : futures) {
        final var response = future.get(20, TimeUnit.SECONDS);
        if (response != null) {
          winners.add(response);
        }
      }
      return winners;
    }
  }

  private CourseEnrollmentResponse enroll(Fixture f, Request request) {
    return enrollments.enrollApplicationCourse(
        f.institutionId(), request.applicationId(), childId(request), assignment(f), null);
  }

  private EnrollApplicationCourseRequest assignment(Fixture f) {
    final var day =
        options.execute(f.institutionId(), f.courseId()).classes().getFirst().days().getFirst();
    final var schedule = day.schedules().getFirst();
    final var slot =
        schedule.individualSlots().isEmpty() ? null : schedule.individualSlots().getFirst().id();
    return new EnrollApplicationCourseRequest(
        f.classId(), List.of(new CourseScheduleAssignmentRequest(schedule.id(), slot)), null);
  }

  private UUID childId(Request request) {
    return tx(
        () ->
            em.find(EnrollmentApplication.class, request.applicationId())
                .getCourseSelections()
                .getFirst()
                .getId());
  }

  private String status(Request request) {
    return tx(
        () ->
            em.find(EnrollmentApplication.class, request.applicationId())
                .getCourseSelections()
                .getFirst()
                .getStatus()
                .name());
  }

  private Long slotCount(Fixture f) {
    return requireNonNull(
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM course_individual_slots WHERE institution_id = ?",
            Long.class,
            f.institutionId()));
  }

  private Request approved(Fixture f, String document) {
    final var request = submitted(f, document);
    approve.execute(f.institutionId(), request.applicationId(), null);
    return request;
  }

  private Request submitted(Fixture f, String document) {
    final var request = draft(f, document);
    select(request, f.courseId());
    applications.updateDraft(
        request.personId(),
        request.applicationId(),
        new UpdateEnrollmentDraftRequest(
            EnrollmentDraftData.builder()
                .academicBackground(
                    AcademicBackgroundDto.builder()
                        .currentlyStudying(false)
                        .educationLevel(EducationLevel.SECONDARY)
                        .schoolOrigin("Colegio")
                        .secondaryCompleted(true)
                        .build())
                .preference(
                    PreferenceDto.builder()
                        .preferredShift("MORNING")
                        .allowsImageUse(false)
                        .isReenrolling(false)
                        .build())
                .build()));
    applications.submitApplication(request.personId(), request.applicationId());
    return request;
  }

  private void select(Request request, UUID courseId) {
    applications.updateDraft(
        request.personId(),
        request.applicationId(),
        new UpdateEnrollmentDraftRequest(
            EnrollmentDraftData.builder()
                .courses(List.of(new CourseSelectionDto(courseId, null)))
                .build()));
  }

  private Request draft(Fixture f, String document) {
    return tx(
        () -> {
          final var institution = em.find(Institution.class, f.institutionId());
          final var person =
              persist(
                  InstitutionalTestData.person(institution, document, LocalDate.of(2000, 1, 1)));
          final var app =
              persist(
                  EnrollmentApplication.createForTrainingPath(
                      institution,
                      person,
                      em.find(TrainingPath.class, f.pathId()),
                      em.find(AcademicYear.class, f.yearId()),
                      em.find(EnrollmentPeriod.class, f.periodId())));
          return new Request(app.getId(), person.getId());
        });
  }

  private Fixture fixture(boolean individual) {
    return tx(
        () -> {
          final var institution =
              InstitutionalTestData.createInstitution(em, "flow-" + UUID.randomUUID());
          roles.provision(institution);
          final var path = persist(TrainingPath.create(institution, "Trayecto", null));
          final var plan =
              persist(StudyPlan.create(institution, path, "Plan", LocalDate.of(2026, 1, 1), null));
          plan.activate();
          final var space =
              persist(
                  AcademicSpace.create(
                      institution,
                      "Curso",
                      null,
                      AcademicSpaceType.SUBJECT,
                      individual ? AcademicSpaceFormat.INDIVIDUAL : AcademicSpaceFormat.GRUPAL,
                      individual));
          final var placement =
              persist(
                  StudyPlanSpace.create(
                      institution,
                      plan,
                      space,
                      null,
                      RequirementType.REQUIRED,
                      1,
                      ApprovalMode.PROMOTION));
          final var year =
              persist(
                  AcademicYear.create(
                      institution,
                      2026,
                      LocalDate.of(2026, 1, 1),
                      LocalDate.of(2026, 12, 31),
                      LocalDate.of(2026, 1, 1)));
          year.transitionTo(AcademicYearStatus.ACTIVE);
          final var instrument =
              individual ? persist(Instrument.create(institution, "Guitarra", null)) : null;
          final var course = persist(Course.create(institution, placement, year, instrument));
          final var courseClass = persist(CourseClass.create(institution, course, 1));
          final var day =
              persist(
                  CourseClassDay.create(
                      institution,
                      courseClass,
                      CourseDay.MONDAY,
                      individual ? 2 : 1,
                      individual ? 30 : null));
          final var schedule =
              persist(
                  CourseClassSchedule.create(
                      institution, day, LocalTime.of(10, 0), LocalTime.of(11, 0)));
          slots.createFor(schedule);
          final var period =
              persist(
                  EnrollmentPeriod.builder()
                      .institution(institution)
                      .academicYear(year)
                      .name("Ingreso")
                      .startDate(Instant.now().minusSeconds(3600))
                      .endDate(Instant.now().plusSeconds(3600))
                      .status(EnrollmentPeriodStatus.OPEN)
                      .build());
          em.flush();
          period.markScopeConfigured();
          final var offering = EnrollmentPeriodOffering.create(period, plan);
          offering.selectLevels(List.of(), true);
          period.getOfferings().add(offering);
          persist(offering);
          return new Fixture(
              institution.getId(),
              path.getId(),
              year.getId(),
              course.getId(),
              courseClass.getId(),
              period.getId());
        });
  }

  private Fixture addCourse(Fixture context, boolean individual, String name, int displayOrder) {
    return tx(
        () -> {
          final var previous = em.find(Course.class, context.courseId());
          final var institution = previous.getInstitution();
          final var space =
              persist(
                  AcademicSpace.create(
                      institution,
                      name,
                      null,
                      AcademicSpaceType.SUBJECT,
                      individual ? AcademicSpaceFormat.INDIVIDUAL : AcademicSpaceFormat.GRUPAL,
                      individual));
          final var placement =
              persist(
                  StudyPlanSpace.create(
                      institution,
                      previous.getStudyPlanSpace().getStudyPlan(),
                      space,
                      null,
                      RequirementType.REQUIRED,
                      displayOrder,
                      ApprovalMode.PROMOTION));
          final var instrument =
              individual ? persist(Instrument.create(institution, name, null)) : null;
          final var course =
              persist(
                  Course.create(
                      institution,
                      placement,
                      requireNonNull(previous.getAcademicYear()),
                      instrument));
          final var courseClass = persist(CourseClass.create(institution, course, 1));
          final var day =
              persist(
                  CourseClassDay.create(
                      institution,
                      courseClass,
                      CourseDay.MONDAY,
                      individual ? 2 : 1,
                      individual ? 30 : null));
          final var schedule =
              persist(
                  CourseClassSchedule.create(
                      institution, day, LocalTime.of(10, 0), LocalTime.of(11, 0)));
          slots.createFor(schedule);
          return new Fixture(
              context.institutionId(),
              context.pathId(),
              context.yearId(),
              course.getId(),
              courseClass.getId(),
              context.periodId());
        });
  }

  private <T> T tx(Supplier<T> action) {
    return new TransactionTemplate(transactions).execute(status -> action.get());
  }

  private <T> T persist(T entity) {
    em.persist(entity);
    return entity;
  }

  private record Fixture(
      UUID institutionId, UUID pathId, UUID yearId, UUID courseId, UUID classId, UUID periodId) {}

  private record Request(UUID applicationId, UUID personId) {}
}
