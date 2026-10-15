package ar.edu.utn.frvm.typeit.boero_api.enrollment.services;

import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.CourseEnrollmentGrade;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads.CourseEnrollmentGradeResponse;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.PersonRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CourseEnrollmentGradeResponseMapper {

  private final PersonRepository personRepository;

  @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
  public List<CourseEnrollmentGradeResponse> toManagementResponses(
      final List<CourseEnrollmentGrade> grades) {
    final Set<UUID> personIds = new HashSet<>();

    for (final CourseEnrollmentGrade grade : grades) {
      if (grade.getCreatedByPersonId() != null) {
        personIds.add(grade.getCreatedByPersonId());
      }

      if (grade.getUpdatedByPersonId() != null) {
        personIds.add(grade.getUpdatedByPersonId());
      }

      if (grade.getPublishedByPersonId() != null) {
        personIds.add(grade.getPublishedByPersonId());
      }
    }

    final Map<UUID, Person> people =
        personIds.isEmpty()
            ? Map.of()
            : personRepository.findAllById(personIds).stream()
                .collect(Collectors.toMap(Person::getId, person -> person));

    return grades.stream().map(grade -> CourseEnrollmentGradeResponse.from(grade, people)).toList();
  }

  @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
  public Map<UUID, Person> resolvePeople(final Set<UUID> personIds) {
    if (personIds.isEmpty()) {
      return Map.of();
    }

    return personRepository.findAllById(personIds).stream()
        .collect(Collectors.toMap(Person::getId, person -> person));
  }
}
