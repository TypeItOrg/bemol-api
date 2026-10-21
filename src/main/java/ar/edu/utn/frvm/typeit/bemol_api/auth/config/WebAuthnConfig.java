package ar.edu.utn.frvm.typeit.bemol_api.auth.config;

import ar.edu.utn.frvm.typeit.bemol_api.auth.services.InstitutionalHostContext;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.PasskeyUserCredentialRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.PasskeyUserEntityRepository;
import ar.edu.utn.frvm.typeit.bemol_api.auth.services.TrustedWebAuthnOperations;
import ar.edu.utn.frvm.typeit.bemol_api.auth.webauthn.WebAuthnOptionsModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRpEntity;
import org.springframework.security.web.webauthn.jackson.WebauthnJacksonModule;
import org.springframework.security.web.webauthn.management.WebAuthnRelyingPartyOperations;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class WebAuthnConfig {

  @Bean
  PublicKeyCredentialRpEntity webauthnRpEntity(final WebAuthnProperties properties) {
    return PublicKeyCredentialRpEntity.builder()
        .id(properties.rpId())
        .name(properties.rpName())
        .build();
  }

  @Bean
  WebAuthnRelyingPartyOperations webauthnRelyingPartyOperations(
      final PasskeyUserEntityRepository userEntityRepository,
      final PasskeyUserCredentialRepository userCredentialRepository,
      final PublicKeyCredentialRpEntity rpEntity,
      final WebAuthnProperties properties,
      final InstitutionalHostContext context) {
    return new TrustedWebAuthnOperations(
        userEntityRepository, userCredentialRepository, rpEntity, properties, context);
  }

  @Bean
  WebauthnJacksonModule webauthnJacksonModule() {
    return new WebauthnJacksonModule();
  }

  @Bean
  ObjectMapper webauthnObjectMapper(final WebauthnJacksonModule springModule) {
    return JsonMapper.builder()
        .addModule(springModule)
        .addModule(new WebAuthnOptionsModule())
        .build();
  }
}
