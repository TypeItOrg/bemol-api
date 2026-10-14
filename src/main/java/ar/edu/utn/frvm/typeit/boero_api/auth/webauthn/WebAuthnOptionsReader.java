package ar.edu.utn.frvm.typeit.boero_api.auth.webauthn;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.security.web.webauthn.api.AttestationConveyancePreference;
import org.springframework.security.web.webauthn.api.AuthenticationExtensionsClientInputs;
import org.springframework.security.web.webauthn.api.AuthenticatorSelectionCriteria;
import org.springframework.security.web.webauthn.api.AuthenticatorSelectionCriteria.AuthenticatorSelectionCriteriaBuilder;
import org.springframework.security.web.webauthn.api.AuthenticatorTransport;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.ImmutableAuthenticationExtensionsClientInput;
import org.springframework.security.web.webauthn.api.ImmutableAuthenticationExtensionsClientInputs;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialDescriptor;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialDescriptor.PublicKeyCredentialDescriptorBuilder;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialParameters;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRpEntity;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialType;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.api.UserVerificationRequirement;
import tools.jackson.databind.JsonNode;

final class WebAuthnOptionsReader {

  private WebAuthnOptionsReader() {}

  static JsonNode required(final JsonNode parent, final String field) {
    final JsonNode child = parent.get(field);
    if (child == null || child.isNull()) {
      throw new IllegalArgumentException("Missing WebAuthn options field");
    }
    return child;
  }

  static void requireObject(final JsonNode node) {
    if (node == null || !node.isObject()) {
      throw new IllegalArgumentException("Invalid WebAuthn options payload");
    }
  }

  static String requiredText(final JsonNode parent, final String field) {
    final JsonNode child = required(parent, field);
    if (!child.isString() || child.asString().isEmpty()) {
      throw new IllegalArgumentException("Invalid WebAuthn options field");
    }
    return child.asString();
  }

  static byte[] requiredBytes(final JsonNode parent, final String field) {
    final byte[] decoded = decodeBase64Url(requiredText(parent, field));
    if (decoded.length == 0) {
      throw new IllegalArgumentException("Invalid WebAuthn options field");
    }
    return decoded;
  }

  private static byte[] decodeBase64Url(final String value) {
    final String padded = value + "=".repeat((4 - (value.length() % 4)) % 4);
    try {
      return Base64.getUrlDecoder().decode(padded);
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("Invalid WebAuthn options field", exception);
    }
  }

  static Duration timeout(final JsonNode node) {
    if (!node.isNumber() || node.asLong() < 0) {
      throw new IllegalArgumentException("Invalid WebAuthn options field");
    }
    return Duration.ofMillis(node.asLong());
  }

  static @Nullable AuthenticationExtensionsClientInputs extensions(final JsonNode node) {
    if (node == null || node.isNull()) {
      return null;
    }
    requireObject(node);
    if (node.size() == 0) {
      return new ImmutableAuthenticationExtensionsClientInputs(List.of());
    }
    final JsonNode credProps = node.get("credProps");
    if (node.size() != 1 || credProps == null || !credProps.isBoolean()) {
      throw new IllegalArgumentException("Unsupported WebAuthn options extensions");
    }
    return new ImmutableAuthenticationExtensionsClientInputs(
        List.of(
            new ImmutableAuthenticationExtensionsClientInput<>(
                "credProps", credProps.asBoolean())));
  }

  static AuthenticationExtensionsClientInputs extensionsOrEmpty(final JsonNode node) {
    final AuthenticationExtensionsClientInputs parsed = extensions(node);
    if (parsed == null) {
      return new ImmutableAuthenticationExtensionsClientInputs(List.of());
    }
    return parsed;
  }

  static PublicKeyCredentialRpEntity rpEntity(final JsonNode node) {
    requireObject(node);
    return PublicKeyCredentialRpEntity.builder()
        .id(requiredText(node, "id"))
        .name(requiredText(node, "name"))
        .build();
  }

  static PublicKeyCredentialUserEntity userEntity(final JsonNode node) {
    requireObject(node);
    return ImmutablePublicKeyCredentialUserEntity.builder()
        .id(new Bytes(requiredBytes(node, "id")))
        .name(requiredText(node, "name"))
        .displayName(requiredText(node, "displayName"))
        .build();
  }

  static List<PublicKeyCredentialParameters> credParams(final JsonNode node) {
    if (!node.isArray()) {
      throw new IllegalArgumentException("Invalid WebAuthn options field");
    }
    final List<PublicKeyCredentialParameters> params = new ArrayList<>();
    for (final JsonNode entry : node) {
      requireObject(entry);
      if (!"public-key".equals(requiredText(entry, "type"))) {
        throw new IllegalArgumentException("Unsupported WebAuthn credential type");
      }
      final JsonNode alg = required(entry, "alg");
      if (!alg.isNumber()) {
        throw new IllegalArgumentException("Invalid WebAuthn options field");
      }
      params.add(WebAuthnOptionValues.credentialAlgorithm(alg.asLong()));
    }
    return List.copyOf(params);
  }

  static List<PublicKeyCredentialDescriptor> descriptors(final JsonNode node) {
    if (!node.isArray()) {
      throw new IllegalArgumentException("Invalid WebAuthn options field");
    }
    final List<PublicKeyCredentialDescriptor> descriptors = new ArrayList<>();
    for (final JsonNode entry : node) {
      descriptors.add(descriptor(entry));
    }
    return List.copyOf(descriptors);
  }

  private static PublicKeyCredentialDescriptor descriptor(final JsonNode node) {
    requireObject(node);
    if (!"public-key".equals(requiredText(node, "type"))) {
      throw new IllegalArgumentException("Unsupported WebAuthn credential type");
    }
    final PublicKeyCredentialDescriptorBuilder builder = PublicKeyCredentialDescriptor.builder();
    builder.type(PublicKeyCredentialType.PUBLIC_KEY);
    builder.id(new Bytes(requiredBytes(node, "id")));
    final JsonNode transports = node.get("transports");
    if (transports != null && !transports.isNull()) {
      builder.transports(transports(transports));
    }
    return builder.build();
  }

  private static AuthenticatorTransport[] transports(final JsonNode node) {
    if (!node.isArray()) {
      throw new IllegalArgumentException("Invalid WebAuthn options field");
    }
    final List<AuthenticatorTransport> transports = new ArrayList<>();
    for (final JsonNode entry : node) {
      if (!entry.isString()) {
        throw new IllegalArgumentException("Invalid WebAuthn options field");
      }
      transports.add(WebAuthnOptionValues.transport(entry.asString()));
    }
    return transports.toArray(AuthenticatorTransport[]::new);
  }

  static AuthenticatorSelectionCriteria selection(final JsonNode node) {
    requireObject(node);
    final AuthenticatorSelectionCriteriaBuilder builder = AuthenticatorSelectionCriteria.builder();
    builder.residentKey(WebAuthnOptionValues.residentKey(requiredText(node, "residentKey")));
    builder.userVerification(userVerification(requiredText(node, "userVerification")));
    final JsonNode attachment = node.get("authenticatorAttachment");
    if (attachment != null && !attachment.isNull()) {
      if (!attachment.isString()) {
        throw new IllegalArgumentException("Invalid WebAuthn options field");
      }
      builder.authenticatorAttachment(WebAuthnOptionValues.attachment(attachment.asString()));
    }
    return builder.build();
  }

  static UserVerificationRequirement userVerification(final String value) {
    return WebAuthnOptionValues.userVerification(value);
  }

  static AttestationConveyancePreference attestation(final String value) {
    return WebAuthnOptionValues.attestation(value);
  }
}
