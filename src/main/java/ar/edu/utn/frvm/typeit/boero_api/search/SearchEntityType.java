package ar.edu.utn.frvm.typeit.boero_api.search;

import static ar.edu.utn.frvm.typeit.boero_api.search.SearchMessages.INVALID_ENTITY_TYPE;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

public enum SearchEntityType {
  INSTITUTION("institution"),
  USER("user"),
  ROLE("role"),
  PLATFORM_ACCOUNT("platform-account"),
  ACADEMIC_YEAR("academic-year"),
  TRAINING_PATH("training-path"),
  STUDY_PLAN("study-plan"),
  ACADEMIC_SPACE("academic-space"),
  INSTRUMENT("instrument"),
  SHIFT("shift"),
  COURSE("course"),
  GUARDIAN_DEPENDENT("guardian-dependent");

  private final String code;

  SearchEntityType(final String code) {
    this.code = code;
  }

  @JsonValue
  public String code() {
    return code;
  }

  public static SearchEntityType fromCode(final String code) {
    return Arrays.stream(values())
        .filter(type -> type.code.equals(code))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException(INVALID_ENTITY_TYPE));
  }
}
