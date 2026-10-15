package ar.edu.utn.frvm.typeit.boero_api.enrollment.services;

import ar.edu.utn.frvm.typeit.boero_api.enrollment.entities.CourseEnrollmentGradeHistory;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.boero_api.institutional.interfaces.PersonRepository;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CourseEnrollmentGradeHistorySupport {

  private final PersonRepository personRepository;

  public Pageable withDefaultSort(final Pageable pageable) {
    if (pageable.getSort().isSorted()) {
      return pageable;
    }

    final Sort defaultSort =
        Sort.by(Sort.Direction.DESC, "changedAt").and(Sort.by(Sort.Direction.DESC, "id"));

    return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), defaultSort);
  }

  @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
  public Map<UUID, Person> resolveActors(final List<CourseEnrollmentGradeHistory> events) {
    final Set<UUID> personIds = new HashSet<>();

    for (final CourseEnrollmentGradeHistory event : events) {
      if (event.getActorPersonId() != null) {
        personIds.add(event.getActorPersonId());
      }
    }

    if (personIds.isEmpty()) {
      return Map.of();
    }

    final Map<UUID, Person> people = new HashMap<>();

    for (final Person person : personRepository.findAllById(personIds)) {
      if (person.getId() != null) {
        people.put(Objects.requireNonNull(person.getId()), person);
      }
    }

    return people;
  }
}
