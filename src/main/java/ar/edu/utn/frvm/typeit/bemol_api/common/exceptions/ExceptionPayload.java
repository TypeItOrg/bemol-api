package ar.edu.utn.frvm.typeit.bemol_api.common.exceptions;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExceptionPayload(
    int status,
    @Nullable String message,
    @Nullable Map<String, String> fieldErrors,
    @Nullable String code) {}
