package ar.edu.utn.frvm.typeit.bemol_api.enrollment.exceptions;

public final class EnrollmentAnswerMessages {
  public static final String INVALID = "Las respuestas del formulario no son válidas.";
  public static final String UNKNOWN_QUESTION =
      "La pregunta no pertenece al formulario de esta inscripción.";
  public static final String TOO_MANY = "El formulario admite como máximo 200 respuestas.";
  public static final String REQUIRED = "Respondé esta pregunta para enviar la inscripción.";
  public static final String TYPE_INVALID = "El tipo de respuesta no corresponde a esta pregunta.";
  public static final String TEXT_INVALID = "La respuesta contiene caracteres no válidos.";
  public static final String SHORT_TEXT_TOO_LONG =
      "La respuesta no puede superar los 500 caracteres.";
  public static final String LONG_TEXT_TOO_LONG =
      "La respuesta no puede superar los 10000 caracteres.";
  public static final String NUMBER_INVALID = "La respuesta debe ser un número finito.";
  public static final String DATE_INVALID =
      "La respuesta debe ser una fecha válida con formato AAAA-MM-DD.";
  public static final String OPTION_INVALID =
      "Seleccioná opciones válidas de esta pregunta, sin repetirlas.";

  private EnrollmentAnswerMessages() {}
}
