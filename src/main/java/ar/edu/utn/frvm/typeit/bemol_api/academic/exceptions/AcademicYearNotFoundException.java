package ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions;

import java.io.Serial;

public class AcademicYearNotFoundException extends AcademicNotFoundException {

  @Serial private static final long serialVersionUID = 1L;

  public AcademicYearNotFoundException() {
    super(AcademicMessages.ACADEMIC_YEAR_NOT_FOUND);
  }
}
