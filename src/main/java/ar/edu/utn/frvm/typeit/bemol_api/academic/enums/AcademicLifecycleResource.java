package ar.edu.utn.frvm.typeit.bemol_api.academic.enums;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.AcademicSpace;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.AcademicYear;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.Course;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.Instrument;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.Shift;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.StudyPlan;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.TrainingPath;
import ar.edu.utn.frvm.typeit.bemol_api.audit.definition.AuditResource;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AcademicLifecycleResource {
  ACADEMIC_YEAR(AcademicYear.AUDIT_TYPE),
  TRAINING_PATH(TrainingPath.AUDIT_TYPE),
  STUDY_PLAN(StudyPlan.AUDIT_TYPE),
  ACADEMIC_SPACE(AcademicSpace.AUDIT_TYPE),
  INSTRUMENT(Instrument.AUDIT_TYPE),
  SHIFT(Shift.AUDIT_TYPE),
  COURSE(Course.AUDIT_TYPE);

  private final AuditResource auditResource;
}
