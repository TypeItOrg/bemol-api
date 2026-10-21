package ar.edu.utn.frvm.typeit.bemol_api.auth.webauthn;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.web.webauthn.api.AuthenticatorAssertionResponse;
import org.springframework.security.web.webauthn.api.AuthenticatorAttestationResponse;
import org.springframework.security.web.webauthn.api.PublicKeyCredential;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialCreationOptions;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRequestOptions;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class WebAuthnOptionsCodec {

  private static final String ENCODE_FAILED = "WebAuthn options could not be serialized.";
  private static final String SNAPSHOT_UNREADABLE = "WebAuthn options snapshot unreadable.";
  private static final String REBUILD_UNREADABLE = "WebAuthn options snapshot rebuild unreadable.";
  private static final String CREDENTIAL_UNREADABLE = "WebAuthn credential unreadable.";

  private final ObjectMapper objectMapper;

  public WebAuthnOptionsCodec(@Qualifier("webauthnObjectMapper") final ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public String encodeCreationOptions(final PublicKeyCredentialCreationOptions options) {
    try {
      return objectMapper.writeValueAsString(options);
    } catch (JacksonException exception) {
      throw new IllegalStateException(ENCODE_FAILED, exception);
    }
  }

  public PublicKeyCredentialCreationOptions decodeCreationOptions(final String json) {
    return rebuildCreationOptions(parseSnapshot(json));
  }

  public JsonNode parseSnapshot(final String json) {
    try {
      return objectMapper.readTree(json);
    } catch (JacksonException | IllegalArgumentException exception) {
      throw new IllegalStateException(SNAPSHOT_UNREADABLE, exception);
    }
  }

  private PublicKeyCredentialCreationOptions rebuildCreationOptions(final JsonNode snapshot) {
    try {
      return objectMapper.treeToValue(snapshot, PublicKeyCredentialCreationOptions.class);
    } catch (JacksonException | IllegalArgumentException exception) {
      throw new IllegalStateException(REBUILD_UNREADABLE, exception);
    }
  }

  public String encodeRequestOptions(final PublicKeyCredentialRequestOptions options) {
    try {
      return objectMapper.writeValueAsString(options);
    } catch (JacksonException exception) {
      throw new IllegalStateException(ENCODE_FAILED, exception);
    }
  }

  public PublicKeyCredentialRequestOptions decodeRequestOptions(final String json) {
    return rebuildRequestOptions(parseSnapshot(json));
  }

  private PublicKeyCredentialRequestOptions rebuildRequestOptions(final JsonNode snapshot) {
    try {
      return objectMapper.treeToValue(snapshot, PublicKeyCredentialRequestOptions.class);
    } catch (JacksonException | IllegalArgumentException exception) {
      throw new IllegalStateException(REBUILD_UNREADABLE, exception);
    }
  }

  public PublicKeyCredential<AuthenticatorAttestationResponse> decodeAttestationCredential(
      final JsonNode node) {
    try {
      return objectMapper.convertValue(
          node, new TypeReference<PublicKeyCredential<AuthenticatorAttestationResponse>>() {});
    } catch (JacksonException | IllegalArgumentException exception) {
      throw new IllegalStateException(CREDENTIAL_UNREADABLE, exception);
    }
  }

  public PublicKeyCredential<AuthenticatorAssertionResponse> decodeAssertionCredential(
      final JsonNode node) {
    try {
      return objectMapper.convertValue(
          node, new TypeReference<PublicKeyCredential<AuthenticatorAssertionResponse>>() {});
    } catch (JacksonException | IllegalArgumentException exception) {
      throw new IllegalStateException(CREDENTIAL_UNREADABLE, exception);
    }
  }
}
