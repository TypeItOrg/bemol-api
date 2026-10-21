package ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions;

import java.io.Serial;

public class StudyPlanNotFoundException extends AcademicNotFoundException {

  @Serial private static final long serialVersionUID = 1L;

  public StudyPlanNotFoundException() {
    super(AcademicMessages.STUDY_PLAN_NOT_FOUND);
  }
}
