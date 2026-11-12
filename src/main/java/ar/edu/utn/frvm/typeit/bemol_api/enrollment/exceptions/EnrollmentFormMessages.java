package ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions;

public final class EnrollmentFormMessages {
  public static final String DRAFT_REVISION_REQUIRED =
      "Indicá la revisión del borrador antes de guardar o enviar.";
  public static final String DRAFT_REVISION_CONFLICT =
      "La inscripción fue modificada. Actualizá los datos antes de guardar o enviar.";
  public static final String FORM_NOT_FOUND =
      "El formulario no existe o no pertenece a la institución.";
  public static final String VERSION_NOT_FOUND =
      "La versión no existe o no pertenece al formulario de la institución.";
  public static final String ASSIGNMENT_NOT_FOUND =
      "La asignación no existe o no pertenece a la institución.";
  public static final String SOURCE_VERSION_INVALID =
      "La nueva versión debe partir de una versión publicada o archivada.";
  public static final String ASSIGNMENT_CONTEXT_NOT_FOUND =
      "El trayecto o ciclo lectivo no está disponible en la institución.";
  public static final String ASSIGNMENT_CONTEXT_INVALID =
      "No se pueden asignar formularios a ciclos lectivos cerrados.";
  public static final String ANSWERS_INVALID = "Las respuestas del formulario no son válidas.";
  public static final String ANSWERS_IMMUTABLE =
      "No se pueden modificar las respuestas de una inscripción cerrada.";
  public static final String NAME_INVALID =
      "El nombre del formulario debe tener entre 1 y 150 caracteres.";
  public static final String DEFINITION_INVALID = "La definición del formulario no es válida.";
  public static final String ESSENTIAL_BLOCKS_REQUIRED =
      "El formulario debe conservar los bloques esenciales de Bemol.";
  public static final String VERSION_NUMBER_INVALID =
      "El número de versión del formulario debe ser positivo.";
  public static final String VERSION_NOT_EDITABLE = "Solo se pueden editar versiones en borrador.";
  public static final String VERSION_NOT_PUBLISHED =
      "La versión del formulario debe estar publicada.";
  public static final String REVISION_CONFLICT =
      "El formulario fue modificado por otra operación. Actualizá los datos e intentá nuevamente.";
  public static final String INSTITUTION_MISMATCH =
      "El formulario y su asignación deben pertenecer a la misma institución.";
  public static final String TRANSITION_DATE_INVALID =
      "La fecha de la transición del formulario no es válida.";
  public static final String VERSION_IN_USE =
      "La versión del formulario todavía tiene asignaciones vigentes.";
  public static final String VERSION_IMMUTABLE =
      "La versión publicada del formulario no puede modificarse ni eliminarse.";
  public static final String ASSIGNMENT_CONFLICT =
      "Ya existe un formulario asignado a ese trayecto y ciclo lectivo.";
  public static final String DRAFT_EXISTS = "El formulario ya tiene una versión en borrador.";
  public static final String APPLICATION_FORM_IMMUTABLE =
      "No se puede cambiar el formulario de una inscripción ya iniciada.";
  public static final String APPLICATION_FORM_CONTEXT_IMMUTABLE =
      "No se puede cambiar el contexto de una inscripción con formulario asignado.";

  private EnrollmentFormMessages() {}
}
