package ar.edu.utn.frvm.typeit.bemol_api.search;

import ar.edu.utn.frvm.typeit.bemol_api.authorization.RequiresPlatformRole;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PlatformRoleCode;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.PaginatedResponse;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.Version;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Validated
@RestController
@RequestMapping("/admin/search")
@RequiresPlatformRole(PlatformRoleCode.PLATFORM_ADMIN)
@RequiredArgsConstructor
public class PlatformSearchController {

  private final SearchService searchService;

  @GetMapping(version = Version.V1)
  public SearchSummaryResponse summary(
      @RequestParam @Size(min = 2, max = 100) @Pattern(regexp = ".*[\\p{L}\\p{N}].*")
          final String search,
      @RequestParam(defaultValue = "5") @Min(1) @Max(5) final int limit) {
    return searchService.platformSummary(search, limit);
  }

  @GetMapping(value = "/{entityType}", version = Version.V1)
  public PaginatedResponse<SearchResultResponse> page(
      @PathVariable final String entityType,
      @RequestParam @Size(min = 2, max = 100) @Pattern(regexp = ".*[\\p{L}\\p{N}].*")
          final String search,
      @RequestParam(defaultValue = "0") @Min(0) final int page,
      @RequestParam(defaultValue = "20") @Min(10) @Max(50) final int size) {
    try {
      return searchService.platformPage(SearchEntityType.fromCode(entityType), search, page, size);
    } catch (IllegalArgumentException exception) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
    }
  }
}
