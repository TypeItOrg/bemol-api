package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.PasskeyCredential;
import ar.edu.utn.frvm.typeit.bemol_api.auth.entities.User;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.web.webauthn.api.AuthenticatorTransport;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.api.ImmutableCredentialRecord;
import org.springframework.security.web.webauthn.api.ImmutableCredentialRecord.ImmutableCredentialRecordBuilder;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCose;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialType;
import org.springframework.stereotype.Component;

@Component
public class PasskeyCredentialMapper {

  public CredentialRecord toRecord(final PasskeyCredential credential) {
    final ImmutableCredentialRecordBuilder builder =
        ImmutableCredentialRecord.builder()
            .credentialType(PublicKeyCredentialType.PUBLIC_KEY)
            .credentialId(new Bytes(decodeBase64Url(credential.getCredentialId())))
            .userEntityUserId(new Bytes(canonicalUserHandle(credential)))
            .publicKey(new ImmutablePublicKeyCose(credential.getPublicKeyCose().clone()))
            .signatureCount(credential.getSignatureCount())
            .uvInitialized(credential.isUvInitialized())
            .transports(toTransports(credential.transportSet()))
            .backupEligible(credential.isBackupEligible())
            .backupState(credential.isBackupState())
            .label(credential.getLabel())
            .created(credential.getCreatedAt());
    if (credential.getAttestationObject() != null) {
      builder.attestationObject(new Bytes(credential.getAttestationObject().clone()));
    }
    if (credential.getAttestationClientDataJson() != null) {
      builder.attestationClientDataJSON(
          new Bytes(credential.getAttestationClientDataJson().clone()));
    }
    if (credential.getLastUsedAt() != null) {
      builder.lastUsed(credential.getLastUsedAt());
    }
    return builder.build();
  }

  public PasskeyCredential toEntity(
      final User user, final CredentialRecord record, final String label) {
    final PasskeyCredential credential =
        PasskeyCredential.builder()
            .user(user)
            .credentialId(record.getCredentialId().toBase64UrlString())
            .publicKeyCose(record.getPublicKey().getBytes().clone())
            .signatureCount(record.getSignatureCount())
            .backupEligible(record.isBackupEligible())
            .backupState(record.isBackupState())
            .uvInitialized(record.isUvInitialized())
            .userHandle(record.getUserEntityUserId().getBytes().clone())
            .attestationObject(
                record.getAttestationObject() == null
                    ? null
                    : record.getAttestationObject().getBytes().clone())
            .attestationClientDataJson(
                record.getAttestationClientDataJSON() == null
                    ? null
                    : record.getAttestationClientDataJSON().getBytes().clone())
            .label(label.trim())
            .build();
    credential.applyTransports(fromTransports(record.getTransports()));
    return credential;
  }

  private static Set<AuthenticatorTransport> toTransports(final Set<String> values) {
    if (values == null || values.isEmpty()) {
      return Set.of();
    }
    final Set<AuthenticatorTransport> transports = new HashSet<>();
    for (final String value : values) {
      try {
        transports.add(AuthenticatorTransport.valueOf(value));
      } catch (IllegalArgumentException ignored) {
        final String normalized = value == null ? "" : value.trim().toUpperCase();
        if (!normalized.isEmpty()) {
          try {
            transports.add(AuthenticatorTransport.valueOf(normalized));
          } catch (IllegalArgumentException again) {
            // ignore unknown transport
          }
        }
      }
    }
    return Set.copyOf(transports);
  }

  private static Set<String> fromTransports(final Set<AuthenticatorTransport> transports) {
    if (transports == null || transports.isEmpty()) {
      return Set.of();
    }
    return transports.stream()
        .map(mappedAuthenticatorTransport -> mappedAuthenticatorTransport.getValue())
        .collect(Collectors.toUnmodifiableSet());
  }

  private static byte[] canonicalUserHandle(final PasskeyCredential credential) {
    final byte[] handle = credential.getUser().getWebauthnUserHandle();
    if (handle == null) {
      throw new IllegalStateException("La credencial no tiene user handle canónico en su usuario.");
    }
    return handle.clone();
  }

  private static byte[] decodeBase64Url(final String value) {
    return Base64.getUrlDecoder().decode(value);
  }
}
