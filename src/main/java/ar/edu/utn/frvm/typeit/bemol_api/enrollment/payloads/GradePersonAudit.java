package ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads;

import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record GradePersonAudit(UUID id, String fullName) {

  public static @Nullable GradePersonAudit resolve(
      final @Nullable UUID personId, final Map<UUID, Person> people) {
    if (personId == null) {
      return null;
    }

    final Person person = people.get(personId);

    if (person == null) {
      return new GradePersonAudit(personId, "Usuario");
    }

    return new GradePersonAudit(personId, person.getFirstName() + " " + person.getLastName());
  }
}
