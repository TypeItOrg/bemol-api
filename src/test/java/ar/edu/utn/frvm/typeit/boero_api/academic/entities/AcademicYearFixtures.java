package ar.edu.utn.frvm.typeit.boero_api.academic.entities;

import ar.edu.utn.frvm.typeit.boero_api.academic.enums.AcademicYearStatus;
import ar.edu.utn.frvm.typeit.boero_api.institutional.entities.Institution;
import java.util.UUID;

/** Persisted academic years with a database identity and real lifecycle behavior. */
public final class AcademicYearFixtures {

  private AcademicYearFixtures() {}

  public static AcademicYear planned(final UUID id, final Institution institution, final int year) {
    return AcademicYear.builder()
        .id(id)
        .institution(institution)
        .year(year)
        .status(AcademicYearStatus.PLANNED)
        .build();
  }
}
