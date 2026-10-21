package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import static java.util.Objects.requireNonNull;

import ar.edu.utn.frvm.typeit.bemol_api.auth.config.InstitutionalDevUserProperties;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.User;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.UserRepository;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.SystemRoleCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.AssignPersonSystemRoleUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.InstitutionRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.PersonRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@Profile({"dev", "test"})
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
@RequiredArgsConstructor
public class InstitutionalDevUserBootstrap implements ApplicationRunner {

  private final InstitutionalDevUserProperties institutionalDevUserProperties;
  private final InstitutionRepository institutionRepository;
  private final PersonRepository personRepository;
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final AssignPersonSystemRoleUseCase assignPersonSystemRoleUseCase;

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (!institutionalDevUserProperties.hasPassword()) {
      log.warn(
          "Institutional dev user bootstrap skipped: app.institutional-dev-user.password is not configured");
      return;
    }

    UUID institutionId = institutionalDevUserProperties.institutionId();
    if (institutionId == null) {
      log.warn(
          "Institutional dev user bootstrap skipped: app.institutional-dev-user.institution-id is not configured");
      return;
    }

    String documentNumber = institutionalDevUserProperties.resolvedDocumentNumber();
    if (documentNumber == null) {
      log.warn(
          "Institutional dev user bootstrap skipped: app.institutional-dev-user.document-number is not configured");
      return;
    }

    Optional<Institution> institution = institutionRepository.findById(institutionId);
    if (institution.isEmpty()) {
      log.warn("Institutional dev user bootstrap skipped: institution {} not found", institutionId);
      return;
    }

    Optional<Person> existingPerson =
        personRepository.findByDocumentNumberAndInstitution_Id(documentNumber, institutionId);

    if (existingPerson.isPresent()) {
      Person person = existingPerson.get();
      if (userRepository
          .findByPerson_IdAndInstitution_Id(person.getId(), institutionId)
          .isPresent()) {
        log.info(
            "Institutional dev user already exists for document {} in institution {}",
            documentNumber,
            institutionId);
        return;
      }
      createUserForPerson(institution.get(), person);
      return;
    }

    createDevUser(institution.get(), documentNumber);
  }

  private void createDevUser(Institution institution, String documentNumber) {
    Person person =
        Person.builder()
            .institution(institution)
            .firstName(institutionalDevUserProperties.resolvedName())
            .lastName(institutionalDevUserProperties.resolvedLastName())
            .documentNumber(documentNumber)
            .email(documentNumber + "@boero.local")
            .build();
    personRepository.save(person);
    createUserForPerson(institution, person);
  }

  private void createUserForPerson(Institution institution, Person person) {
    User user =
        User.builder()
            .institution(institution)
            .person(person)
            .password(
                requireNonNull(passwordEncoder.encode(institutionalDevUserProperties.password())))
            .build();
    userRepository.save(user);
    assignPersonSystemRoleUseCase.execute(person, SystemRoleCode.APPLICANT, false);
    log.info(
        "Institutional dev user ensured for document {} in institution {}",
        person.getDocumentNumber(),
        institution.getId());
  }
}
