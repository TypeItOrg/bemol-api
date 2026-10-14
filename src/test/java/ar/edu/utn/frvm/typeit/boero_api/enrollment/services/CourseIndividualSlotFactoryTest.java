package ar.edu.utn.frvm.typeit.boero_api.enrollment.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ar.edu.utn.frvm.typeit.boero_api.academic.entities.Course;
import ar.edu.utn.frvm.typeit.boero_api.academic.entities.CourseClass;
import ar.edu.utn.frvm.typeit.boero_api.academic.entities.CourseClassDay;
import ar.edu.utn.frvm.typeit.boero_api.academic.entities.CourseClassSchedule;
import ar.edu.utn.frvm.typeit.boero_api.academic.enums.CourseDay;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.CourseIndividualSlot;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.interfaces.CourseIndividualSlotRepository;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CourseIndividualSlotFactoryTest {
  private final Institution institution = Institution.builder().id(UUID.randomUUID()).build();
  private final CourseClass courseClass = CourseClass.create(institution, new Course(), 1);
  @Mock private CourseIndividualSlotRepository repository;
  @Captor private ArgumentCaptor<List<CourseIndividualSlot>> slots;

  @ParameterizedTest
  @CsvSource({
    "10:00,11:00,10:00/10:30;10:30/11:00",
    "23:00,23:30,23:00/23:30",
    "10:00:30,11:00:30,10:00:30/10:30:30;10:30:30/11:00:30"
  })
  void createsEveryPeriodExactlyOnceWithItsScheduleAndTenant(
      String start, String end, String expectedPeriods) {
    final var day = CourseClassDay.create(institution, courseClass, CourseDay.MONDAY, null, 30);
    final var schedule =
        CourseClassSchedule.create(institution, day, LocalTime.parse(start), LocalTime.parse(end));

    new CourseIndividualSlotFactory(repository).createFor(schedule);

    verify(repository).saveAll(slots.capture());
    final var expected =
        Arrays.stream(expectedPeriods.split(";"))
            .map(period -> period.split("/"))
            .map(period -> tuple(LocalTime.parse(period[0]), LocalTime.parse(period[1])))
            .toList();
    assertThat(slots.getValue())
        .extracting(slot -> slot.getStartTime(), slot -> slot.getEndTime())
        .containsExactlyElementsOf(expected);
    assertThat(slots.getValue())
        .allSatisfy(
            slot -> {
              assertThat(slot.getInstitution()).isSameAs(institution);
              assertThat(slot.getSchedule()).isSameAs(schedule);
            });
  }

  @Test
  void doesNotCreatePeriodsForGroupClasses() {
    final var day = CourseClassDay.create(institution, courseClass, CourseDay.MONDAY, 20, null);
    final var schedule =
        CourseClassSchedule.create(institution, day, LocalTime.of(10, 0), LocalTime.of(11, 0));

    new CourseIndividualSlotFactory(repository).createFor(schedule);

    verifyNoInteractions(repository);
  }
}
