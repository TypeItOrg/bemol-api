package ar.edu.utn.frvm.typeit.bemol_api.audit.envers;

import java.util.List;

/** Explicit response allowlist, independent of future Envers mapping additions. */
final class IdentityEnversHistoryTables {
  private IdentityEnversHistoryTables() {}

  static final List<EnversHistoryTable> TABLES =
      List.of(
          EnversHistoryTable.of(
              "institutions",
              "Institución",
              "institution_id",
              "active:BOOLEAN logo_size:NUMBER city_id:REFERENCE logo_content_type:TEXT logo_version:TEXT public_subdomain:TEXT slug:TEXT name:TEXT"),
          EnversHistoryTable.of(
              "passkey_credentials",
              "Passkey",
              "passkey_credential_id",
              "revoked_at:DATETIME user_id:REFERENCE"),
          EnversHistoryTable.of(
              "people", "Persona", "person_id", "deleted:BOOLEAN institution_id:REFERENCE"),
          EnversHistoryTable.of(
              "person_access_invitations",
              "Invitación de acceso propio",
              "invitation_id",
              "consumed_at:DATETIME expires_at:DATETIME institutional_issuer_person_id:REFERENCE issuing_institution_id:REFERENCE person_id:REFERENCE representation_id:REFERENCE"),
          EnversHistoryTable.of(
              "person_guardian_attachments",
              "Adjunto de vinculación",
              "person_guardian_attachment_id",
              "file_size:NUMBER institution_id:REFERENCE person_guardian_id:REFERENCE content_type:TEXT"),
          EnversHistoryTable.of(
              "person_guardians",
              "Vinculación",
              "person_guardian_id",
              "is_primary_contact:BOOLEAN own_access_requested:BOOLEAN resolved_at:DATETIME dependent_person_id:REFERENCE institution_id:REFERENCE resolved_by_person_id:REFERENCE tutor_person_id:REFERENCE status:TEXT relationship:TEXT"),
          EnversHistoryTable.of(
              "person_role_assignment_training_paths",
              "Trayecto de asignación de rol",
              "person_role_assignment_id training_path_id",
              "training_path_id:REFERENCE"),
          EnversHistoryTable.of(
              "person_role_assignments",
              "Asignación de rol",
              "person_role_assignment_id",
              "institution_id:REFERENCE person_id:REFERENCE role_id:REFERENCE access_scope:TEXT"),
          EnversHistoryTable.of(
              "platform_account_roles",
              "Rol de cuenta de plataforma",
              "platform_account_role_id",
              "platform_account_id:REFERENCE role_id:REFERENCE"),
          EnversHistoryTable.of(
              "platform_accounts",
              "Cuenta de plataforma",
              "platform_account_id",
              "enabled:BOOLEAN"),
          EnversHistoryTable.of(
              "role_permissions",
              "Permiso del rol",
              "role_id permission_id",
              "permission_id:REFERENCE"),
          EnversHistoryTable.of(
              "roles",
              "Rol",
              "role_id",
              "is_system:BOOLEAN institution_id:REFERENCE scope:TEXT code:TEXT name:TEXT"),
          EnversHistoryTable.of(
              "students",
              "Estudiante",
              "student_id",
              "enrollment_date:DATE institution_id:REFERENCE person_id:REFERENCE status:TEXT"),
          EnversHistoryTable.of(
              "users",
              "Cuenta institucional",
              "user_id",
              "enabled:BOOLEAN self_access_enabled:BOOLEAN email_verified_at:DATETIME institution_id:REFERENCE person_id:REFERENCE email_verification_status:TEXT"));
}
