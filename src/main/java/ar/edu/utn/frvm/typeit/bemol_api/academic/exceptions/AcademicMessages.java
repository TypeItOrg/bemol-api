package ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions;

public final class AcademicMessages {
  public static final String DOCUMENT_INSTITUTION_CHANGE_BLOCKED =
      "No se puede cambiar la institución porque el documento ya fue utilizado. Creá una copia en la otra institución.";
  public static final String DOCUMENT_NOT_FOUND = "El documento no existe en esta institución.";
  public static final String DOCUMENT_REVISION_CONFLICT =
      "El documento o su asignación cambió. Recargá la información antes de guardar.";
  public static final String DOCUMENT_DUPLICATE_ASSIGNMENT =
      "El documento está asignado al trayecto. Recargá para configurar o reactivar esa asignación.";
  public static final String DOCUMENT_ASSIGNMENT_IMMUTABLE =
      "No se puede cambiar el documento de una asignación. Retirala y seleccioná otro documento.";

  public static final String ACADEMIC_YEAR_NOT_FOUND = "El ciclo lectivo especificado no existe.";
  public static final String TRAINING_PATH_NOT_FOUND =
      "El trayecto formativo especificado no existe.";
  public static final String STUDY_PLAN_NOT_FOUND = "El plan de estudio especificado no existe.";
  public static final String ACADEMIC_LEVEL_NOT_FOUND =
      "El nivel académico especificado no existe.";
  public static final String ACADEMIC_SPACE_NOT_FOUND =
      "El espacio académico especificado no existe.";
  public static final String INSTRUMENT_NOT_FOUND = "El instrumento especificado no existe.";
  public static final String SHIFT_NOT_FOUND = "El turno especificado no existe.";
  public static final String COURSE_NOT_FOUND = "El curso especificado no existe.";
  public static final String COURSE_ALREADY_EXISTS =
      "Ya existe un curso para ese espacio académico en el ciclo lectivo indicado.";
  public static final String STUDY_PLAN_SPACE_NOT_FOUND =
      "El espacio del plan especificado no existe.";
  public static final String PREREQUISITE_NOT_FOUND = "La correlatividad especificada no existe.";
  public static final String DUPLICATE_NAME =
      "Ya existe un recurso con ese nombre en la institución.";
  public static final String DUPLICATE_YEAR = "Ya existe un ciclo lectivo para ese año.";
  public static final String DUPLICATE_ORDER = "El orden indicado ya está en uso.";
  public static final String DUPLICATE_PLAN_SPACE =
      "El espacio académico ya está incorporado en ese nivel del plan.";
  public static final String DUPLICATE_PREREQUISITE =
      "La correlatividad ya está configurada para esa instancia.";
  public static final String STUDY_PLAN_SPACE_HAS_PREREQUISITES =
      "El espacio no puede eliminarse porque participa en una correlatividad. Eliminá las correlatividades asociadas antes de continuar.";
  public static final String STUDY_PLAN_CURRICULUM_REQUIRES_DRAFT =
      "La estructura curricular solo puede modificarse mientras el plan de estudio está en borrador.";
  public static final String ACADEMIC_LEVEL_HAS_SPACES =
      "El nivel no puede eliminarse porque contiene espacios académicos. Quitá esos espacios antes de continuar.";
  public static final String TRAINING_PATH_HAS_ACTIVE_PLANS =
      "El trayecto formativo no puede desactivarse porque tiene planes de estudio activos asociados.";
  public static final String STUDY_PLAN_HAS_ACTIVE_COURSES =
      "El plan de estudio no puede desactivarse porque tiene cursos que no están cerrados asociados.";
  public static final String COURSE_STUDY_PLAN_NOT_ACTIVE =
      "El plan de estudio seleccionado debe estar publicado y no eliminado.";
  public static final String COURSE_YEAR_NOT_ACTIVE =
      "El ciclo lectivo seleccionado debe estar activo.";
  public static final String COURSE_SPACE_NOT_IN_PLAN =
      "El espacio académico indicado no pertenece al plan de estudio seleccionado.";
  public static final String COURSE_REQUIRES_CLASS = "El curso debe tener al menos una clase.";
  public static final String ACADEMIC_SPACE_HAS_ACTIVE_OR_DRAFT_PLANS =
      "El espacio académico no puede desactivarse porque está utilizado en un plan de estudio activo o en borrador.";
  public static final String ACADEMIC_SPACE_FORMAT_HAS_COURSES =
      "El formato del espacio académico no puede modificarse porque ya tiene cursos asociados.";
  public static final String ACADEMIC_SPACE_INSTRUMENTAL_HAS_COURSES =
      "La instrumentalidad del espacio académico no puede modificarse porque ya tiene cursos asociados.";
  public static final String COURSE_DUPLICATE =
      "Ya existe un curso con la misma identidad curricular en el ciclo lectivo indicado.";
  public static final String INSTRUMENT_REQUIRED =
      "El espacio académico instrumental requiere un instrumento.";
  public static final String INSTRUMENT_NOT_ALLOWED =
      "El espacio académico no instrumental no admite un instrumento.";
  public static final String INSTRUMENT_IN_USE =
      "El instrumento no puede desactivarse porque está siendo utilizado por cursos activos.";
  public static final String STUDY_PLAN_ACTIVATION_REQUIRES_START_AND_ACTIVE_PATH =
      "El plan no puede activarse hasta completar la fecha de inicio y tener un trayecto formativo activo.";
  public static final String STUDY_PLAN_ACTIVATION_REQUIRES_SPACES =
      "El plan no puede activarse porque todavía no tiene espacios académicos incorporados.";
  public static final String STUDY_PLAN_STATUS_TRANSITION_INVALID =
      "La transición de estado solicitada no es válida para el plan de estudio.";
  public static final String INVALID_STATE = "La transición de estado solicitada no es válida.";
  public static final String INVALID_RELATIONSHIP =
      "La relación académica especificada no es válida.";
  public static final String PREREQUISITE_CYCLE = "La correlatividad solicitada genera un ciclo.";
  public static final String ACADEMIC_YEAR_ACTIVE_CONFLICT =
      "La institución ya tiene un ciclo lectivo activo.";
  public static final String ACADEMIC_YEAR_DATES_REQUIRED =
      "Las fechas de inicio y fin son requeridas para activar el ciclo lectivo.";
  public static final String ACADEMIC_YEAR_DATES_INVALID =
      "Las fechas del ciclo lectivo no son válidas.";
  public static final String ACADEMIC_YEAR_OUT_OF_RANGE =
      "El año debe estar entre 2000 y el año siguiente al actual.";
  public static final String ACADEMIC_YEAR_START_DATE_INVALID =
      "La fecha de inicio debe pertenecer al año del ciclo lectivo.";
  public static final String ACADEMIC_YEAR_END_DATE_INVALID =
      "La fecha de finalización debe pertenecer al año del ciclo lectivo o al siguiente.";
  public static final String STUDY_PLAN_END_DATE_REQUIRED =
      "La fecha final es requerida al inactivar el plan.";
  public static final String STUDY_PLAN_END_DATE_INVALID =
      "La fecha final no puede ser anterior al inicio del plan.";
  public static final String STUDY_PLAN_END_DATE_CANNOT_BE_FUTURE =
      "La fecha de finalización no puede ser posterior a la fecha actual.";
  public static final String STUDY_PLAN_VERSION_REQUIRES_ORDERED_DATES =
      "La nueva versión necesita una fecha de inicio para ordenar la vigencia.";
  public static final String STUDY_PLAN_VERSION_DATE_OVERLAP =
      "La vigencia de la nueva versión se superpone con la versión anterior.";
  public static final String STUDY_PLAN_VERSION_SOURCE_INVALID =
      "Solo se puede crear una versión a partir de un plan publicado o retirado.";
  public static final String STUDY_PLAN_VERSION_ALREADY_EXISTS =
      "El plan ya tiene una versión posterior vigente o en preparación.";
  public static final String STUDY_PLAN_VERSION_START_REQUIRED =
      "Definí la fecha de inicio de la nueva versión.";
  public static final String STUDY_PLAN_DATES_INVALID = "La vigencia del plan no es válida.";
  public static final String STUDY_PLAN_START_DATE_REQUIRED =
      "Completá la fecha de inicio antes de indicar una fecha final.";
  public static final String DATE_PAIR_REQUIRED = "Completá ambas fechas o dejá ambas vacías.";
  public static final String INVALID_VALUE = "El valor indicado no es válido.";
  public static final String CONFLICTING_INSTRUMENT_FILTERS =
      "No se puede seleccionar un instrumento y filtrar cursos sin instrumento a la vez.";

  public static final String INVALID_SORT_FIELD = "El campo de ordenamiento indicado no es válido.";
  public static final String COURSE_DAY_DUPLICATED =
      "Cada día solo puede configurarse una vez por clase.";
  public static final String COURSE_SCHEDULE_INVALID = "El horario indicado no es válido.";
  public static final String COURSE_SCHEDULE_OVERLAP =
      "Los horarios de un mismo día no pueden superponerse.";
  public static final String COURSE_PERIOD_DURATION_REQUIRED =
      "La duración del período es requerida para los espacios académicos individuales.";
  public static final String COURSE_PERIOD_DURATION_NOT_DIVISIBLE =
      "La duración total de los horarios de cada día debe ser divisible por la duración del período.";
  public static final String COURSE_TEACHERS_INVALID =
      "Alguno de los docentes indicados no tiene el rol de docente en la institución.";
  public static final String INVALID_NAME_FORMAT = "El nombre no tiene un formato válido.";
  public static final String INVALID_DISPLAY_ORDER = "El orden debe ser mayor que cero.";
  public static final String DELETE_REFERENCED_RESOURCE =
      "El recurso no puede eliminarse porque todavía tiene elementos vigentes asociados.";
  public static final String RESTORE_PARENT_UNAVAILABLE =
      "El recurso no puede restaurarse porque su elemento superior no está disponible.";
  public static final String RESTORE_CONFLICT =
      "El recurso no puede restaurarse porque sus datos ya están en uso.";
  public static final String COURSE_RESTORE_INSTRUMENT_MISMATCH =
      "El curso no puede restaurarse porque su instrumento no coincide con la instrumentalidad actual del espacio académico.";
  public static final String ACADEMIC_LIFECYCLE_ACTOR_NOT_FOUND =
      "No fue posible identificar al actor de la operación.";

  private AcademicMessages() {}
}
