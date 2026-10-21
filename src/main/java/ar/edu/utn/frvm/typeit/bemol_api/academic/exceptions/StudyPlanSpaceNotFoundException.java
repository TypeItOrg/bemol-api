package ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions;

import java.io.Serial;

public class StudyPlanSpaceNotFoundException extends AcademicNotFoundException {

  @Serial private static final long serialVersionUID = 1L;

  public StudyPlanSpaceNotFoundException() {
    super(AcademicMessages.STUDY_PLAN_SPACE_NOT_FOUND);
  }
}
