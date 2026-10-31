package ar.edu.utn.frvm.typeit.bemol_api.support;

import com.webauthn4j.converter.util.ObjectConverter;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.web.webauthn.api.Bytes;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public final class PasskeyAuthenticator {
  private final KeyPair key;
  private final byte[] credentialId = Bytes.random().getBytes();
  private final JsonMapper json = JsonMapper.builder().build();
  private int counter;

  public PasskeyAuthenticator() throws Exception {
    final var generator = KeyPairGenerator.getInstance("EC");
    generator.initialize(new ECGenParameterSpec("secp256r1"));
    key = generator.generateKeyPair();
  }

  public JsonNode register(final JsonNode options, final String origin) throws Exception {
    final var point = ((ECPublicKey) key.getPublic()).getW();
    final Map<Integer, Object> cose = new LinkedHashMap<>();
    cose.put(1, 2);
    cose.put(3, -7);
    cose.put(-1, 1);
    cose.put(-2, coordinate(point.getAffineX()));
    cose.put(-3, coordinate(point.getAffineY()));
    final var bytes = new ByteArrayOutputStream();
    final var data = new DataOutputStream(bytes);
    data.write(authData(options.path("rp").path("id").asString(), 0x45));
    data.write(new byte[16]);
    data.writeShort(credentialId.length);
    data.write(credentialId);
    final var cbor = new ObjectConverter().getCborConverter();
    data.write(cbor.writeValueAsBytes(cose));
    return credential(
        Map.of(
            "attestationObject",
                encode(
                    cbor.writeValueAsBytes(
                        Map.of(
                            "fmt", "none", "attStmt", Map.of(), "authData", bytes.toByteArray()))),
            "clientDataJSON",
                encode(clientData("webauthn.create", options.path("challenge").asString(), origin)),
            "transports", new String[] {"internal"}));
  }

  public JsonNode authenticate(
      final JsonNode options, final String origin, final byte[] userHandle, final boolean verified)
      throws Exception {
    counter++;
    final byte[] client = clientData("webauthn.get", options.path("challenge").asString(), origin);
    final byte[] auth = authData(options.path("rpId").asString(), verified ? 0x05 : 0x01);
    final var signature = Signature.getInstance("SHA256withECDSA");
    signature.initSign(key.getPrivate());
    signature.update(auth);
    signature.update(MessageDigest.getInstance("SHA-256").digest(client));
    return credential(
        Map.of(
            "authenticatorData",
            encode(auth),
            "clientDataJSON",
            encode(client),
            "signature",
            encode(signature.sign()),
            "userHandle",
            encode(userHandle)));
  }

  private byte[] authData(final String rp, final int flags) throws Exception {
    final var bytes = new ByteArrayOutputStream();
    final var data = new DataOutputStream(bytes);
    data.write(MessageDigest.getInstance("SHA-256").digest(rp.getBytes(StandardCharsets.UTF_8)));
    data.writeByte(flags);
    data.writeInt(counter);
    return bytes.toByteArray();
  }

  private byte[] clientData(final String type, final String challenge, final String origin) {
    return json.writeValueAsBytes(
        Map.of("type", type, "challenge", challenge, "origin", origin, "crossOrigin", false));
  }

  private JsonNode credential(final Map<String, Object> response) {
    return json.valueToTree(
        Map.of(
            "id",
            encode(credentialId),
            "rawId",
            encode(credentialId),
            "type",
            "public-key",
            "response",
            response,
            "clientExtensionResults",
            Map.of()));
  }

  private byte[] coordinate(final BigInteger number) {
    final byte[] source = number.toByteArray();
    final byte[] result = new byte[32];
    final int count = Math.min(32, source.length);
    System.arraycopy(source, source.length - count, result, 32 - count, count);
    return result;
  }

  private String encode(final byte[] bytes) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}
