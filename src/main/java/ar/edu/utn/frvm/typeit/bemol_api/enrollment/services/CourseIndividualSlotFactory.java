package ar.edu.utn.frvm.typeit.bemol_api.enrollment.services;

import static java.util.Objects.requireNonNull;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.CourseClassSchedule;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.CourseIndividualSlot;
import ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces.CourseIndividualSlotRepository;
import java.util.ArrayList;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CourseIndividualSlotFactory {
  private final CourseIndividualSlotRepository repository;

  public void createFor(final CourseClassSchedule schedule) {
    final int count = schedule.individualPeriodCount();
    if (count == 0) {
      return;
    }
    final int duration = requireNonNull(schedule.getDay().getPeriodDurationMinutes());
    final var slots = new ArrayList<CourseIndividualSlot>(count);
    for (int index = 0; index < count; index++) {
      final var start = schedule.getStartTime().plusMinutes((long) index * duration);
      slots.add(
          CourseIndividualSlot.create(
              schedule.getInstitution(), schedule, start, start.plusMinutes(duration)));
    }
    repository.saveAll(slots);
  }
}
