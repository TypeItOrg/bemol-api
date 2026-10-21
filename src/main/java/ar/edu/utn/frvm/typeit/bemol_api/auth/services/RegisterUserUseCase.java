package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import static java.util.Objects.requireNonNull;

import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditAction;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditEntityType;
import ar.edu.utn.frvm.typeit.bemol_api.audit.enums.AuditOrigin;
import ar.edu.utn.frvm.typeit.bemol_api.audit.services.AuditEventRecorder;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.User;
import ar.edu.utn.frvm.typeit.bemol_api.auth.enums.EmailVerificationStatus;
import ar.edu.utn.frvm.typeit.bemol_api.auth.exceptions.UserAlreadyExistsException;
import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.UserRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.requests.RegisterRequest;
import ar.edu.utn.frvm.typeit.bemol_api.auth.payloads.responses.UserRegisteredResponse;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.SystemRoleCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.AssignPersonSystemRoleUseCase;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Institution;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.entities.Person;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.InstitutionInactiveException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.InstitutionNotFoundException;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.InstitutionRepository;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.interfaces.PersonRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RegisterUserUseCase {
  private final InstitutionalHostContext hostContext;

  private final InstitutionalEmailVerificationUseCase emailVerification;
  private final UserRepository userRepository;
  private final InstitutionRepository institutionRepository;
  private final PersonRepository personRepository;
  private final PasswordEncoder passwordEncoder;
  private final Validator validator;
  private final AssignPersonSystemRoleUseCase assignPersonSystemRoleUseCase;

  private final AuditEventRecorder auditEventRecorder;

  @Transactional
  public UserRegisteredResponse execute(final RegisterRequest request) {
    hostContext.requireInstitution(request.institutionId());

    Institution institution =
        institutionRepository
            .findById(request.institutionId())
            .orElseThrow(InstitutionNotFoundException::new);

    if (!institution.isActive()) {
      throw new InstitutionInactiveException();
    }

    if (personRepository.existsByDocumentNumberAndInstitution_Id(
        request.documentNumber(), request.institutionId())) {
      throw new UserAlreadyExistsException();
    }

    final SystemRoleCode roleCode = SystemRoleCode.APPLICANT;
    Person person =
        Person.builder()
            .institution(institution)
            .firstName(request.name())
            .lastName(request.lastName())
            .birthDate(request.birthDate())
            .documentNumber(request.documentNumber())
            .email(request.email())
            .build();
    assertPersonValid(person);
    try {
      person = personRepository.save(person);
      personRepository.flush();
    } catch (DataIntegrityViolationException exception) {
      throw new UserAlreadyExistsException();
    }

    User user =
        User.builder()
            .institution(institution)
            .person(person)
            .emailVerificationStatus(EmailVerificationStatus.PENDING)
            .password(requireNonNull(passwordEncoder.encode(request.password())))
            .build();
    user = userRepository.save(user);
    assignPersonSystemRoleUseCase.execute(person, roleCode, false);
    emailVerification.sendInitial(user);
    auditEventRecorder.recordCurrentActor(
        institution,
        person.getId(),
        AuditAction.INSTITUTIONAL_ACCOUNT_CREATED,
        AuditEntityType.USER,
        user.getId(),
        AuditOrigin.SELF_REGISTRATION,
        Map.of(
            "initialRole",
            roleCode.name(),
            "emailVerificationRequired",
            true,
            "emailVerificationStatus",
            user.getEmailVerificationStatus().name(),
            "personCreated",
            true));
    return UserRegisteredResponse.builder()
        .emailVerificationRequired(true)
        .userId(user.getId())
        .documentNumber(user.getDocumentNumber())
        .institutionId(user.getInstitutionId())
        .build();
  }

  private void assertPersonValid(Person person) {
    Set<ConstraintViolation<Person>> violations = validator.validate(person);
    if (!violations.isEmpty()) {
      throw new ConstraintViolationException(violations);
    }
  }
}
