package ar.edu.utn.frvm.typeit.bemol_api.common.utils;

public final class HeaderUtils {

  private HeaderUtils() {}

  public static String bearerValue(String authorization) {
    if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
      return authorization.substring(7).trim();
    }
    return "";
  }
}
