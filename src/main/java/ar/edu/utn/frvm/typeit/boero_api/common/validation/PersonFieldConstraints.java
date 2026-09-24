package ar.edu.utn.frvm.typeit.boero_api.common.validation;

public final class PersonFieldConstraints {

  private PersonFieldConstraints() {}

  public static final int NAME_MIN = 3;
  public static final int NAME_MAX = 255;
  public static final int MINIMUM_AGE = 3;
  public static final int ADULT_AGE = 18;

  public static final int DOCUMENT_LENGTH = 8;
  public static final String DOCUMENT_PATTERN = "^[0-9]{" + DOCUMENT_LENGTH + "}$";

  public static final String NAME_PATTERN = "^[\\p{L} ]+$";

  public static final String EMAIL_PATTERN = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$";

  public static final int PASSWORD_MIN = 8;
  public static final int PASSWORD_MAX = 255;

  public static final int PASSWORD_HASH_MAX = 255;

  public static final String CHECK_SQL_PEOPLE_DOCUMENT_NUMBER =
      "document_number ~ '" + DOCUMENT_PATTERN + "'";

  public static final String CHECK_SQL_PEOPLE_FIRST_NAME_LENGTH =
      "char_length(first_name) >= " + NAME_MIN + " AND char_length(first_name) <= " + NAME_MAX;

  public static final String CHECK_SQL_PEOPLE_LAST_NAME_LENGTH =
      "char_length(last_name) >= " + NAME_MIN + " AND char_length(last_name) <= " + NAME_MAX;
}
