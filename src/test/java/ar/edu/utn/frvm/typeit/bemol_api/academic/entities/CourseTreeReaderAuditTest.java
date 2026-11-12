package ar.edu.utn.frvm.typeit.bemol_api.academic.entities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import ar.edu.utn.frvm.typeit.bemol_api.academic.enums.CourseStatus;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.CourseClassDayRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.CourseClassRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.CourseClassScheduleRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.CourseClassTeacherRepository;
import ar.edu.utn.frvm.typeit.bemol_api.academic.services.CourseTreeReader;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CourseTreeReaderAuditTest {
  private final CourseClassRepository classes = mock(CourseClassRepository.class);
  private final CourseClassDayRepository days = mock(CourseClassDayRepository.class);
  private final CourseClassScheduleRepository schedules = mock(CourseClassScheduleRepository.class);
  private final CourseClassTeacherRepository teachers = mock(CourseClassTeacherRepository.class);
  private final CourseTreeReader reader = new CourseTreeReader(classes, days, schedules, teachers);

  @Test
  void oneBatchProducesBothResponseAndSnapshot() {
    final var course = Course.builder().id(UUID.randomUUID()).status(CourseStatus.ACTIVE).build();
    final var courseClass = CourseClass.builder().id(UUID.randomUUID()).classNumber(1).build();
    when(classes.findByCourse_IdOrderByClassNumberAsc(course.getId()))
        .thenReturn(List.of(courseClass));

    final var result = reader.readWithAudit(course);

    assertThat(result.classes()).hasSize(1);
    assertThat(result.snapshot().values()).containsEntry("classCount", 1);
    verify(classes).findByCourse_IdOrderByClassNumberAsc(course.getId());
    verify(days).findByCourseClass_IdIn(List.of(courseClass.getId()));
    verify(schedules).findByDay_IdIn(List.of());
    verify(teachers).findByCourseClass_IdIn(List.of(courseClass.getId()));
    verifyNoMoreInteractions(classes, days, schedules, teachers);
  }

  @Test
  void emptyCourseDoesNotLoadChildTables() {
    final var course = Course.builder().id(UUID.randomUUID()).status(CourseStatus.ACTIVE).build();

    final var result = reader.readWithAudit(course);

    assertThat(result.classes()).isEmpty();
    assertThat(result.teacherIds()).isEmpty();
    assertThat(result.snapshot().values()).containsEntry("classCount", 0);
    verifyNoInteractions(days, schedules, teachers);
  }
}
