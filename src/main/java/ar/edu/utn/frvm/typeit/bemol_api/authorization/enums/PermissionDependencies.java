package ar.edu.utn.frvm.typeit.bemol_api.authorization.enums;

import java.util.EnumSet;
import java.util.Set;

final class PermissionDependencies {
  private PermissionDependencies() {}

  static Set<PermissionCode> required(final PermissionCode permission) {
    return switch (permission) {
      case INSTITUTION_PERSON_CREATE,
          INSTITUTION_PERSON_UPDATE_ANY,
          INSTITUTION_PERSON_DELETE,
          INSTITUTION_USER_STATUS_UPDATE ->
          Set.of(PermissionCode.INSTITUTION_PERSON_READ_ANY);
      case INSTITUTION_ROLE_ASSIGN, INSTITUTION_ROLE_REVOKE ->
          Set.of(PermissionCode.INSTITUTION_PERSON_READ_ANY, PermissionCode.INSTITUTION_ROLE_READ);
      case INSTITUTION_ROLE_CREATE, INSTITUTION_ROLE_UPDATE, INSTITUTION_ROLE_DELETE ->
          Set.of(PermissionCode.INSTITUTION_ROLE_READ);
      case INSTITUTION_UPDATE -> Set.of(PermissionCode.INSTITUTION_READ);
      case ACADEMIC_YEAR_CREATE,
          ACADEMIC_YEAR_UPDATE,
          ACADEMIC_YEAR_STATUS_UPDATE,
          ACADEMIC_YEAR_DELETE,
          ACADEMIC_YEAR_RESTORE ->
          Set.of(PermissionCode.ACADEMIC_YEAR_READ);
      case TRAINING_PATH_CREATE,
          TRAINING_PATH_UPDATE,
          TRAINING_PATH_STATUS_UPDATE,
          TRAINING_PATH_DELETE,
          TRAINING_PATH_RESTORE ->
          Set.of(PermissionCode.TRAINING_PATH_READ);
      case STUDY_PLAN_CREATE,
          STUDY_PLAN_UPDATE,
          STUDY_PLAN_STATUS_UPDATE,
          STUDY_PLAN_CURRICULUM_UPDATE,
          STUDY_PLAN_DELETE,
          STUDY_PLAN_RESTORE ->
          Set.of(PermissionCode.STUDY_PLAN_READ);
      case ACADEMIC_SPACE_CREATE,
          ACADEMIC_SPACE_UPDATE,
          ACADEMIC_SPACE_STATUS_UPDATE,
          ACADEMIC_SPACE_DELETE,
          ACADEMIC_SPACE_RESTORE ->
          Set.of(PermissionCode.ACADEMIC_SPACE_READ);
      case INSTRUMENT_CREATE,
          INSTRUMENT_UPDATE,
          INSTRUMENT_STATUS_UPDATE,
          INSTRUMENT_DELETE,
          INSTRUMENT_RESTORE ->
          Set.of(PermissionCode.INSTRUMENT_READ);
      case ENROLLMENT_PERIOD_CREATE,
          ENROLLMENT_PERIOD_UPDATE,
          ENROLLMENT_PERIOD_STATUS_UPDATE,
          ENROLLMENT_PERIOD_DELETE ->
          Set.of(PermissionCode.ENROLLMENT_PERIOD_READ);
      case SHIFT_CREATE, SHIFT_UPDATE, SHIFT_STATUS_UPDATE, SHIFT_DELETE, SHIFT_RESTORE ->
          Set.of(PermissionCode.SHIFT_READ);
      case COURSE_CREATE, COURSE_UPDATE, COURSE_STATUS_UPDATE, COURSE_DELETE, COURSE_RESTORE ->
          Set.of(PermissionCode.COURSE_READ);
      case DOCUMENT_CATALOG_MANAGE -> Set.of(PermissionCode.DOCUMENT_CATALOG_READ);
      case ENROLLMENT_ATTACHMENT_READ, ENROLLMENT_APPLICATION_COURSE_READ ->
          Set.of(PermissionCode.ENROLLMENT_APPLICATION_READ);
      case ENROLLMENT_DOCUMENT_REQUEST_CREATE,
          ENROLLMENT_ATTACHMENT_UPLOAD,
          ENROLLMENT_ATTACHMENT_DELETE,
          ENROLLMENT_ATTACHMENT_REVIEW ->
          Set.of(PermissionCode.ENROLLMENT_ATTACHMENT_READ);
      case ENROLLMENT_APPLICATION_APPROVE, ENROLLMENT_APPLICATION_REJECT ->
          Set.of(PermissionCode.ENROLLMENT_APPLICATION_READ);
      case COURSE_ENROLLMENT_CREATE,
          COURSE_ENROLLMENT_WITHDRAW,
          COURSE_ENROLLMENT_ACADEMIC_STATUS_UPDATE,
          COURSE_WAITLIST_READ ->
          Set.of(PermissionCode.COURSE_ENROLLMENT_READ);
      case ENROLLMENT_APPLICATION_COURSE_ENROLL, ENROLLMENT_APPLICATION_COURSE_REJECT ->
          Set.of(
              PermissionCode.ENROLLMENT_APPLICATION_COURSE_READ,
              PermissionCode.ENROLLMENT_APPLICATION_READ);
      case OWN_ENROLLMENT_APPLICATION_MANAGE ->
          Set.of(PermissionCode.OWN_ENROLLMENT_APPLICATION_READ);
      case TEACHING_ACADEMIC_STATUS_UPDATE -> Set.of(PermissionCode.TEACHING_READ);
      default -> Set.of();
    };
  }

  static Set<PermissionCode> expand(final Set<PermissionCode> permissions) {
    final EnumSet<PermissionCode> expanded = EnumSet.noneOf(PermissionCode.class);
    expanded.addAll(permissions);
    boolean changed;

    do {
      changed =
          expanded.addAll(
              expanded.stream().flatMap(code -> code.requiredPermissions().stream()).toList());
    } while (changed);

    return Set.copyOf(expanded);
  }
}
