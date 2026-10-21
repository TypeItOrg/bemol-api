package ar.edu.utn.frvm.typeit.bemol_api.audit.exceptions;

public final class AuditMessages {
  public static final String INVALID_DETAILS =
      "Los detalles del evento deben ser un objeto válido.";
  public static final String INVALID_ACTOR = "El actor de auditoría no es válido.";
  public static final String INVALID_REPRESENTATION =
      "La representación requiere personas institucionales distintas.";
  public static final String INVALID_DATE_RANGE =
      "La fecha desde debe ser anterior a la fecha hasta.";
  public static final String INVALID_ACTOR_FILTER =
      "Para filtrar por actor seleccioná un tipo de actor institucional o de plataforma.";
  public static final String INVALID_SCOPE_FILTER =
      "El ámbito de plataforma no admite una institución.";
  public static final String EVENT_NOT_FOUND = "El evento de auditoría no existe.";

  private AuditMessages() {}
}
