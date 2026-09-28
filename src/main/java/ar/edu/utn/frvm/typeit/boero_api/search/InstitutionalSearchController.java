package ar.edu.utn.frvm.typeit.boero_api.search;

import ar.edu.utn.frvm.typeit.boero_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.boero_api.authorization.RequiresInstitutionAccess;
import ar.edu.utn.frvm.typeit.boero_api.authorization.services.AuthorizationService;
import ar.edu.utn.frvm.typeit.boero_api.common.web.Version;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/institutions/{institutionId}/search")
@RequiresInstitutionAccess
@RequiredArgsConstructor
public class InstitutionalSearchController {

  private final SearchService searchService;
  private final AuthorizationService authorizationService;

  @GetMapping(version = Version.V1)
  public SearchSummaryResponse summary(
      @PathVariable final UUID institutionId,
      @RequestParam @Size(min = 2, max = 100) @Pattern(regexp = ".*[\\p{L}\\p{N}].*")
          final String search,
      @RequestParam(defaultValue = "5") @Min(1) @Max(5) final int limit,
      final Authentication authentication) {
    final UUID personId =
        authentication.getPrincipal() instanceof JwtAuthenticatedUser user ? user.personId() : null;

    return searchService.institutionalSummary(
        institutionId,
        personId,
        search,
        limit,
        authorizationService.resolvePermissions(authentication));
  }
}
