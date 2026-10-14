package ar.edu.utn.frvm.typeit.boero_api.academic.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ar.edu.utn.frvm.typeit.boero_api.academic.entities.Course;
import ar.edu.utn.frvm.typeit.boero_api.academic.entities.CourseClassDay;
import ar.edu.utn.frvm.typeit.boero_api.academic.entities.CourseClassSchedule;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.AcademicSpaceFormat;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.CourseDay;
import ar.edu.utn.frvm.typeit.boero_api.academic.exceptions.AcademicMessages;
import ar.edu.utn.frvm.typeit.boero_api.academic.exceptions.AcademicValidationException;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.CourseClassDayRepository;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.CourseClassRepository;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.CourseClassScheduleRepository;
import ar.edu.utn.frvm.typeit.boero_api.academic.interfaces.CourseClassTeacherRepository;
import ar.edu.utn.frvm.typeit.boero_api.academic.payloads.CourseClassDayRequest;
import ar.edu.utn.frvm.typeit.boero_api.academic.payloads.CourseClassRequest;
import ar.edu.utn.frvm.typeit.boero_api.academic.payloads.CourseClassScheduleRequest;
import ar.edu.utn.frvm.typeit.boero_api.authorization.interfaces.PersonRoleAssignmentRepository;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.services.CourseIndividualSlotFactory;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.PersonRepository;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CourseClassAssemblerTest {

  @Mock private CourseClassRepository courseClassRepository;
  @Mock private CourseClassDayRepository courseClassDayRepository;
  @Mock private CourseClassScheduleRepository courseClassScheduleRepository;
  @Mock private CourseClassTeacherRepository courseClassTeacherRepository;
  @Mock private PersonRoleAssignmentRepository personRoleAssignmentRepository;
  @Mock private PersonRepository personRepository;
  @Mock private CourseIndividualSlotFactory slotFactory;

  private CourseClassAssembler assembler;

  private final Institution institution = Institution.builder().id(UUID.randomUUID()).build();
  private final Course course = new Course();
  private final Person person = Person.builder().institution(institution).build();

  @BeforeEach
  void setUp() {
    assembler =
        new CourseClassAssembler(
            courseClassRepository,
            courseClassDayRepository,
            courseClassScheduleRepository,
            courseClassTeacherRepository,
            personRoleAssignmentRepository,
            personRepository,
            slotFactory);
  }

  private void stubClassAndDayPersistence() {
    given(courseClassRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));
    given(courseClassDayRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));
    given(personRepository.findByIdAndInstitution_Id(any(), any())).willReturn(Optional.of(person));
  }

  private void stubAssemblyPersistence() {
    stubClassAndDayPersistence();
    given(courseClassScheduleRepository.save(any()))
        .willAnswer(invocation -> invocation.getArgument(0));
  }

  private void stubValidTeachers() {
    given(
            personRoleAssignmentRepository.countDistinctPersonsByInstitutionAndRoleCode(
                any(), anyList(), any()))
        .willReturn(1L);
  }

  private CourseClassRequest individualMondayClass() {
    return new CourseClassRequest(
        List.of(UUID.randomUUID()),
        List.of(
            new CourseClassDayRequest(
                CourseDay.MONDAY,
                null,
                60,
                List.of(
                    new CourseClassScheduleRequest(LocalTime.of(14, 0), LocalTime.of(18, 0)),
                    new CourseClassScheduleRequest(LocalTime.of(18, 30), LocalTime.of(19, 30))))));
  }

  @Test
  @DisplayName("Should assemble classes and compute capacity for individual spaces")
  void assemblesIndividualClassesWithComputedCapacity() {
    stubValidTeachers();
    stubAssemblyPersistence();

    final var classes =
        assembler.assemble(
            institution, course, AcademicSpaceFormat.INDIVIDUAL, List.of(individualMondayClass()));

    assertThat(classes).hasSize(1);
    assertThat(classes.getFirst().getInstitution()).isSameAs(institution);
    assertThat(classes.getFirst().getCourse()).isSameAs(course);
    assertThat(classes.getFirst().getClassNumber()).isEqualTo(1);
    verifySavedDay(5, 60, CourseDay.MONDAY);
  }

  @Test
  @DisplayName("Should keep optional grupal capacity and clear the period duration")
  void keepsGrupalOptionalCapacity() {
    stubValidTeachers();
    stubAssemblyPersistence();
    final var request =
        new CourseClassRequest(
            List.of(UUID.randomUUID()),
            List.of(
                new CourseClassDayRequest(
                    CourseDay.TUESDAY,
                    null,
                    45,
                    List.of(
                        new CourseClassScheduleRequest(LocalTime.of(8, 0), LocalTime.of(10, 0))))));

    final var classes =
        assembler.assemble(institution, course, AcademicSpaceFormat.GRUPAL, List.of(request));

    assertThat(classes).hasSize(1);
    verifySavedDay(null, null, CourseDay.TUESDAY);
  }

  @ParameterizedTest
  @CsvSource({
    "23:00,23:30:30",
    "10:00,11:00:30",
    "10:00,11:00:00.000000001",
    "10:00,10:15",
    "10:00,10:45"
  })
  @DisplayName("Should reject schedules whose total duration is not divisible by the period")
  void rejectsIndivisibleSchedules(String start, String end) {
    stubValidTeachers();
    stubClassAndDayPersistence();
    final var request =
        new CourseClassRequest(
            List.of(UUID.randomUUID()),
            List.of(
                new CourseClassDayRequest(
                    CourseDay.WEDNESDAY,
                    null,
                    30,
                    List.of(
                        new CourseClassScheduleRequest(
                            LocalTime.parse(start), LocalTime.parse(end))))));

    assertThatThrownBy(
            () ->
                assembler.assemble(
                    institution, course, AcademicSpaceFormat.INDIVIDUAL, List.of(request)))
        .isInstanceOf(AcademicValidationException.class)
        .hasMessage(AcademicMessages.COURSE_PERIOD_DURATION_NOT_DIVISIBLE);
    verifyNoInteractions(courseClassScheduleRepository, slotFactory);
  }

  @Test
  @DisplayName("Should reject overlapping schedules within the same day")
  void rejectsOverlappingSchedules() {
    stubValidTeachers();
    final var request =
        new CourseClassRequest(
            List.of(UUID.randomUUID()),
            List.of(
                new CourseClassDayRequest(
                    CourseDay.THURSDAY,
                    10,
                    null,
                    List.of(
                        new CourseClassScheduleRequest(LocalTime.of(14, 0), LocalTime.of(18, 0)),
                        new CourseClassScheduleRequest(
                            LocalTime.of(17, 0), LocalTime.of(19, 0))))));

    assertThatThrownBy(
            () ->
                assembler.assemble(
                    institution, course, AcademicSpaceFormat.GRUPAL, List.of(request)))
        .isInstanceOf(AcademicValidationException.class);
  }

  @Test
  @DisplayName("Should allow contiguous schedules within the same day")
  void allowsContiguousSchedules() {
    stubValidTeachers();
    stubAssemblyPersistence();
    final var request =
        new CourseClassRequest(
            List.of(UUID.randomUUID()),
            List.of(
                new CourseClassDayRequest(
                    CourseDay.THURSDAY,
                    10,
                    null,
                    List.of(
                        new CourseClassScheduleRequest(LocalTime.of(14, 0), LocalTime.of(16, 0)),
                        new CourseClassScheduleRequest(
                            LocalTime.of(16, 0), LocalTime.of(18, 0))))));

    final var classes =
        assembler.assemble(institution, course, AcademicSpaceFormat.GRUPAL, List.of(request));

    assertThat(classes).hasSize(1);
    verifySavedDay(10, null, CourseDay.THURSDAY);
    final var schedules = ArgumentCaptor.forClass(CourseClassSchedule.class);
    verify(courseClassScheduleRepository, times(2)).save(schedules.capture());
    assertThat(schedules.getAllValues())
        .extracting(schedule -> schedule.getStartTime(), schedule -> schedule.getEndTime())
        .containsExactly(
            tuple(LocalTime.of(14, 0), LocalTime.of(16, 0)),
            tuple(LocalTime.of(16, 0), LocalTime.of(18, 0)));
    for (final var schedule : schedules.getAllValues()) {
      assertThat(schedule.getInstitution()).isSameAs(institution);
      assertThat(schedule.getDay().getCourseClass()).isSameAs(classes.getFirst());
      verify(slotFactory).createFor(schedule);
    }
  }

  @Test
  @DisplayName("Should reject a day configured twice in the same class")
  void rejectsDuplicatedDays() {
    stubValidTeachers();
    final var day =
        new CourseClassDayRequest(
            CourseDay.FRIDAY,
            10,
            null,
            List.of(new CourseClassScheduleRequest(LocalTime.of(8, 0), LocalTime.of(10, 0))));

    assertThatThrownBy(
            () ->
                assembler.assemble(
                    institution,
                    course,
                    AcademicSpaceFormat.GRUPAL,
                    List.of(new CourseClassRequest(List.of(UUID.randomUUID()), List.of(day, day)))))
        .isInstanceOf(AcademicValidationException.class);
  }

  @Test
  @DisplayName("Should reject teachers without the teacher role")
  void rejectsInvalidTeachers() {
    given(
            personRoleAssignmentRepository.countDistinctPersonsByInstitutionAndRoleCode(
                any(), anyList(), any()))
        .willReturn(0L);

    assertThatThrownBy(
            () ->
                assembler.assemble(
                    institution,
                    course,
                    AcademicSpaceFormat.GRUPAL,
                    List.of(individualMondayClass())))
        .isInstanceOf(AcademicValidationException.class);
  }

  private void verifySavedDay(
      final @Nullable Integer capacity,
      final @Nullable Integer periodMinutes,
      final CourseDay dayOfWeek) {
    final var day = ArgumentCaptor.forClass(CourseClassDay.class);
    verify(courseClassDayRepository).save(day.capture());
    assertThat(day.getValue().getCapacity()).isEqualTo(capacity);
    assertThat(day.getValue().getPeriodDurationMinutes()).isEqualTo(periodMinutes);
    assertThat(day.getValue().getDayOfWeek()).isEqualTo(dayOfWeek);
    assertThat(day.getValue().getInstitution()).isSameAs(institution);
  }
}
