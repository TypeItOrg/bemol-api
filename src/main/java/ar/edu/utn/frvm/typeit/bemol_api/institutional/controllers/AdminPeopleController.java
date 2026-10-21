package ar.edu.utn.frvm.typeit.bemol_api.institutional.controllers;

import ar.edu.utn.frvm.typeit.bemol_api.authorization.RequiresPlatformRole;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PlatformRoleCode;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.PaginatedResponse;
import ar.edu.utn.frvm.typeit.bemol_api.common.web.Version;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.person.CreatePersonRequest;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.person.PersonResponse;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.person.PersonSummaryResponse;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.person.UpdatePersonByAdminRequest;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.services.CreatePersonUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.services.DeletePersonUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.services.GetPersonByIdUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.services.ListPeopleUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.services.UpdatePersonByAdminUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/admin/institutions/{institutionId}/people")
@RequiredArgsConstructor
@RequiresPlatformRole(PlatformRoleCode.PLATFORM_ADMIN)
public class AdminPeopleController {

  private final ListPeopleUseCase listPeopleUseCase;
  private final GetPersonByIdUseCase getPersonByIdUseCase;
  private final CreatePersonUseCase createPersonUseCase;
  private final UpdatePersonByAdminUseCase updatePersonByAdminUseCase;
  private final DeletePersonUseCase deletePersonUseCase;

  @GetMapping(version = Version.V1)
  public PaginatedResponse<PersonSummaryResponse> list(
      @PathVariable final UUID institutionId,
      @RequestParam(required = false) @Size(max = 100) final String search,
      @RequestParam(required = false) final UUID roleId,
      @PageableDefault(size = 20, sort = "lastName", direction = Sort.Direction.ASC)
          final Pageable pageable) {
    return listPeopleUseCase.execute(institutionId, search, roleId, pageable);
  }

  @GetMapping(value = "/{personId}", version = Version.V1)
  public PersonResponse get(
      @PathVariable final UUID institutionId, @PathVariable final UUID personId) {
    return getPersonByIdUseCase.execute(institutionId, personId);
  }

  @PostMapping(version = Version.V1)
  @ResponseStatus(HttpStatus.CREATED)
  public PersonResponse create(
      @PathVariable final UUID institutionId,
      @Valid @RequestBody final CreatePersonRequest request) {
    return createPersonUseCase.execute(institutionId, request);
  }

  @PutMapping(value = "/{personId}", version = Version.V1)
  public PersonResponse update(
      @PathVariable final UUID institutionId,
      @PathVariable final UUID personId,
      @Valid @RequestBody final UpdatePersonByAdminRequest request) {
    return updatePersonByAdminUseCase.execute(institutionId, personId, request);
  }

  @DeleteMapping(value = "/{personId}", version = Version.V1)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable final UUID institutionId, @PathVariable final UUID personId) {
    deletePersonUseCase.execute(institutionId, personId);
  }
}
