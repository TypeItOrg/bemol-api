package ar.edu.utn.frvm.typeit.bemol_api.common.exceptions;

import java.io.Serial;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public abstract class ApplicationException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  private final ErrorCategory category;
  private final @Nullable Map<String, String> fieldErrors;
  private final @Nullable String code;

  protected ApplicationException(final ErrorCategory category, final String message) {
    this(category, message, null, null);
  }

  protected ApplicationException(
      final ErrorCategory category,
      final String message,
      final @Nullable Map<String, String> fieldErrors) {
    this(category, message, fieldErrors, null);
  }

  protected ApplicationException(
      final ErrorCategory category, final String message, final @Nullable String code) {
    this(category, message, null, code);
  }

  protected ApplicationException(
      final ErrorCategory category,
      final String message,
      final @Nullable Map<String, String> fieldErrors,
      final @Nullable String code) {
    super(message);
    this.category = category;
    this.fieldErrors = fieldErrors == null ? null : Map.copyOf(fieldErrors);
    this.code = code;
  }

  public ErrorCategory category() {
    return category;
  }

  public @Nullable Map<String, String> fieldErrors() {
    return fieldErrors;
  }

  public @Nullable String code() {
    return code;
  }
}
