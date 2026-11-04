package ar.edu.utn.frvm.typeit.bemol_api.institutional.entities;

import static ar.edu.utn.frvm.typeit.bemol_api.common.validation.PersonFieldConstraints.CHECK_SQL_PEOPLE_DOCUMENT_NUMBER;
import static ar.edu.utn.frvm.typeit.bemol_api.common.validation.PersonFieldConstraints.CHECK_SQL_PEOPLE_FIRST_NAME_LENGTH;
import static ar.edu.utn.frvm.typeit.bemol_api.common.validation.PersonFieldConstraints.CHECK_SQL_PEOPLE_LAST_NAME_LENGTH;
import static ar.edu.utn.frvm.typeit.bemol_api.common.validation.PersonFieldConstraints.DOCUMENT_LENGTH;
import static ar.edu.utn.frvm.typeit.bemol_api.common.validation.PersonFieldConstraints.DOCUMENT_PATTERN;
import static ar.edu.utn.frvm.typeit.bemol_api.common.validation.PersonFieldConstraints.MINIMUM_AGE;
import static ar.edu.utn.frvm.typeit.bemol_api.common.validation.PersonFieldConstraints.NAME_MAX;
import static ar.edu.utn.frvm.typeit.bemol_api.common.validation.PersonFieldConstraints.NAME_MIN;
import static ar.edu.utn.frvm.typeit.bemol_api.common.validation.PersonFieldConstraints.NAME_PATTERN;

import ar.edu.utn.frvm.typeit.bemol_api.common.persistence.Auditable;
import ar.edu.utn.frvm.typeit.bemol_api.common.persistence.GeneratedUUIDv7;
import ar.edu.utn.frvm.typeit.bemol_api.common.validation.MinimumAge;
import ar.edu.utn.frvm.typeit.bemol_api.common.validation.ValidationMessages;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.InstitutionMessages;
import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;

@Entity
@DynamicUpdate
@Table(
    name = "people",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "people_institution_id_id_unique",
          columnNames = {"institution_id", "person_id"})
    },
    check = {
      @CheckConstraint(
          name = "people_document_number_format",
          constraint = CHECK_SQL_PEOPLE_DOCUMENT_NUMBER),
      @CheckConstraint(
          name = "people_first_name_length",
          constraint = CHECK_SQL_PEOPLE_FIRST_NAME_LENGTH),
      @CheckConstraint(
          name = "people_last_name_length",
          constraint = CHECK_SQL_PEOPLE_LAST_NAME_LENGTH),
      @CheckConstraint(
          name = "people_phone_number_required",
          constraint = "phone_number ~ '[^[:space:]]'")
    },
    indexes =
        @Index(name = "people_institution_deleted_idx", columnList = "institution_id, deleted"))
@Getter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Person extends Auditable {

  @Id
  @GeneratedUUIDv7
  @Column(name = "person_id")
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "institution_id", nullable = false)
  @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
  private Institution institution;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "address_id")
  private Address address;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "birth_city_id")
  private City birthCity;

  @NotNull(message = ValidationMessages.NATIONALITY_REQUIRED)
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "nationality_country_id", nullable = false)
  private Country nationalityCountry;

  @NotBlank(message = ValidationMessages.DOCUMENT_REQUIRED)
  @Pattern(regexp = DOCUMENT_PATTERN, message = ValidationMessages.DOCUMENT_FORMAT)
  @Column(name = "document_number", nullable = false, length = DOCUMENT_LENGTH)
  private String documentNumber;

  @NotBlank(message = ValidationMessages.FIRST_NAME_REQUIRED)
  @Size.List({
    @Size(min = NAME_MIN, message = ValidationMessages.FIRST_NAME_MIN_LENGTH),
    @Size(max = NAME_MAX, message = ValidationMessages.FIRST_NAME_MAX_LENGTH)
  })
  @Pattern(regexp = NAME_PATTERN, message = ValidationMessages.FIRST_NAME_FORMAT)
  @Column(name = "first_name", nullable = false, length = NAME_MAX)
  private String firstName;

  @NotBlank(message = ValidationMessages.LAST_NAME_REQUIRED)
  @Size.List({
    @Size(min = NAME_MIN, message = ValidationMessages.LAST_NAME_MIN_LENGTH),
    @Size(max = NAME_MAX, message = ValidationMessages.LAST_NAME_MAX_LENGTH)
  })
  @Pattern(regexp = NAME_PATTERN, message = ValidationMessages.LAST_NAME_FORMAT)
  @Column(name = "last_name", nullable = false, length = NAME_MAX)
  private String lastName;

  @Column(name = "birth_date")
  @MinimumAge(MINIMUM_AGE)
  private LocalDate birthDate;

  @NotBlank(message = ValidationMessages.PHONE_REQUIRED)
  @Size(max = 30, message = ValidationMessages.PHONE_MAX_LENGTH)
  @Column(name = "phone_number", nullable = false, length = 30)
  private String phoneNumber;

  // People without personal access may have no email.
  @Email(message = ValidationMessages.PERSON_EMAIL_FORMAT)
  @Size(max = 150, message = ValidationMessages.PERSON_EMAIL_MAX_LENGTH)
  @Column(length = 150)
  private String email;

  @Column(name = "deleted", nullable = false)
  @Builder.Default
  @Audited
  private boolean deleted = false;

  public void correctDocumentNumber(final String documentNumber) {
    this.documentNumber = documentNumber;
  }

  public void updateIdentity(
      final String firstName,
      final String lastName,
      final LocalDate birthDate,
      final City birthCity,
      final Country nationalityCountry) {
    this.firstName = firstName;
    this.lastName = lastName;
    this.birthDate = birthDate;
    this.birthCity = birthCity;
    this.nationalityCountry = nationalityCountry;
  }

  public void updateContact(final String email, final String phoneNumber) {
    this.email = email;
    this.phoneNumber = phoneNumber;
  }

  public void changeAddress(final Address address) {
    if (address != null && !institution.getId().equals(address.getInstitution().getId())) {
      throw new IllegalArgumentException(InstitutionMessages.PERSON_ADDRESS_INSTITUTION_MISMATCH);
    }

    this.address = address;
  }

  public boolean delete() {
    if (deleted) {
      return false;
    }

    deleted = true;
    return true;
  }
}
