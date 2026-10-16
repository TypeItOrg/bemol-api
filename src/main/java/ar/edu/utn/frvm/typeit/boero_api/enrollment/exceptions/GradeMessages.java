package ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions;

public final class GradeMessages {
  public static final String GRADE_NOT_FOUND = "La nota especificada no existe.";
  public static final String EVALUATION_REQUIRED = "La evaluación es obligatoria.";
  public static final String EVALUATION_TOO_LONG =
      "La evaluación no debe superar los 150 caracteres.";
  public static final String EVALUATION_DUPLICATE =
      "Ya existe una nota con esa evaluación para esta cursada.";
  public static final String VALUE_REQUIRED = "La nota es obligatoria.";
  public static final String VALUE_OUT_OF_RANGE = "La nota debe estar entre 1 y 10.";
  public static final String VALUE_TOO_MANY_DECIMALS = "La nota admite como máximo dos decimales.";
  public static final String VALUE_INVALID = "La nota no es válida.";
  public static final String ENROLLMENT_NOT_ENROLLED =
      "Solo se pueden gestionar notas de cursadas en estado Cursando.";
  public static final String GRADE_PENDING_DELETION_IMMUTABLE =
      "La nota está pendiente de eliminación y no puede modificarse. Cancele la eliminación antes de editarla.";
  public static final String GRADE_DRAFT_DELETE_DIRECTLY =
      "La nota todavía es un borrador y debe eliminarse directamente.";
  public static final String GRADE_PENDING_DELETION_PUBLISH =
      "La nota pendiente de eliminación no puede publicarse individualmente. Publique la clase.";
  public static final String GRADE_ENROLLMENT_MISMATCH =
      "La nota no pertenece a la cursada indicada.";
  public static final String GRADE_CLASS_MISMATCH = "La cursada no pertenece a la clase indicada.";
  public static final String GRADE_VERSION_STALE =
      "La nota fue modificada por otra operación. Actualizá los datos e intentá nuevamente.";
  public static final String PUBLISH_NO_CHANGES =
      "La clase no tiene cambios pendientes de notas para publicar.";
  public static final String TEACHER_NOT_ASSIGNED = "No tenés asignada esta clase como docente.";

  private GradeMessages() {}
}
