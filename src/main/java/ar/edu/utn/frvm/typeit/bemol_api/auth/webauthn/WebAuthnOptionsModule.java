package ar.edu.utn.frvm.typeit.bemol_api.auth.webauthn;

import static ar.edu.utn.frvm.typeit.bemol_api.auth.webauthn.WebAuthnOptionsReader.attestation;
import static ar.edu.utn.frvm.typeit.bemol_api.auth.webauthn.WebAuthnOptionsReader.credParams;
import static ar.edu.utn.frvm.typeit.bemol_api.auth.webauthn.WebAuthnOptionsReader.descriptors;
import static ar.edu.utn.frvm.typeit.bemol_api.auth.webauthn.WebAuthnOptionsReader.extensions;
import static ar.edu.utn.frvm.typeit.bemol_api.auth.webauthn.WebAuthnOptionsReader.extensionsOrEmpty;
import static ar.edu.utn.frvm.typeit.bemol_api.auth.webauthn.WebAuthnOptionsReader.requireObject;
import static ar.edu.utn.frvm.typeit.bemol_api.auth.webauthn.WebAuthnOptionsReader.required;
import static ar.edu.utn.frvm.typeit.bemol_api.auth.webauthn.WebAuthnOptionsReader.requiredBytes;
import static ar.edu.utn.frvm.typeit.bemol_api.auth.webauthn.WebAuthnOptionsReader.requiredText;
import static ar.edu.utn.frvm.typeit.bemol_api.auth.webauthn.WebAuthnOptionsReader.rpEntity;
import static ar.edu.utn.frvm.typeit.bemol_api.auth.webauthn.WebAuthnOptionsReader.selection;
import static ar.edu.utn.frvm.typeit.bemol_api.auth.webauthn.WebAuthnOptionsReader.timeout;
import static ar.edu.utn.frvm.typeit.bemol_api.auth.webauthn.WebAuthnOptionsReader.userEntity;
import static ar.edu.utn.frvm.typeit.bemol_api.auth.webauthn.WebAuthnOptionsReader.userVerification;

import java.io.Serial;
import org.springframework.security.web.webauthn.api.AuthenticationExtensionsClientInputs;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialCreationOptions;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialCreationOptions.PublicKeyCredentialCreationOptionsBuilder;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRequestOptions;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRequestOptions.PublicKeyCredentialRequestOptionsBuilder;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.module.SimpleModule;

public class WebAuthnOptionsModule extends SimpleModule {

  @Serial private static final long serialVersionUID = 1L;

  public WebAuthnOptionsModule() {
    addDeserializer(PublicKeyCredentialCreationOptions.class, new CreationOptionsDeserializer());
    addDeserializer(PublicKeyCredentialRequestOptions.class, new RequestOptionsDeserializer());
  }

  static final class CreationOptionsDeserializer
      extends ValueDeserializer<PublicKeyCredentialCreationOptions> {

    @Override
    public PublicKeyCredentialCreationOptions deserialize(
        final JsonParser parser, final DeserializationContext context) throws JacksonException {
      final JsonNode root = parser.readValueAsTree();
      requireObject(root);
      final PublicKeyCredentialCreationOptionsBuilder builder =
          PublicKeyCredentialCreationOptions.builder();
      builder.rp(rpEntity(required(root, "rp")));
      builder.user(userEntity(required(root, "user")));
      builder.challenge(new Bytes(requiredBytes(root, "challenge")));
      builder.pubKeyCredParams(credParams(required(root, "pubKeyCredParams")));
      builder.timeout(timeout(required(root, "timeout")));
      builder.excludeCredentials(descriptors(required(root, "excludeCredentials")));
      builder.authenticatorSelection(selection(required(root, "authenticatorSelection")));
      builder.attestation(attestation(requiredText(root, "attestation")));
      final AuthenticationExtensionsClientInputs extensions = extensions(root.get("extensions"));
      if (extensions != null) {
        builder.extensions(extensions);
      }
      return builder.build();
    }
  }

  static final class RequestOptionsDeserializer
      extends ValueDeserializer<PublicKeyCredentialRequestOptions> {

    @Override
    public PublicKeyCredentialRequestOptions deserialize(
        final JsonParser parser, final DeserializationContext context) throws JacksonException {
      final JsonNode root = parser.readValueAsTree();
      requireObject(root);
      final PublicKeyCredentialRequestOptionsBuilder builder =
          PublicKeyCredentialRequestOptions.builder();
      builder.challenge(new Bytes(requiredBytes(root, "challenge")));
      builder.timeout(timeout(required(root, "timeout")));
      builder.rpId(requiredText(root, "rpId"));
      builder.allowCredentials(descriptors(required(root, "allowCredentials")));
      builder.userVerification(userVerification(requiredText(root, "userVerification")));
      builder.extensions(extensionsOrEmpty(root.get("extensions")));
      return builder.build();
    }
  }
}
