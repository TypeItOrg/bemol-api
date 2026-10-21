package ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions;

import java.io.Serial;

public class CourseNotFoundException extends AcademicNotFoundException {

  @Serial private static final long serialVersionUID = 1L;

  public CourseNotFoundException() {
    super(AcademicMessages.COURSE_NOT_FOUND);
  }
}
