package ar.edu.utn.frvm.typeit.bemol_api.enrollment.pdf.render;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class ChromiumSeccompProfileTest {
  @Test
  void preservesThePinnedUpstreamProfileOutsideTheFiveChromiumRules() throws Exception {
    final var profile = profile();
    final var extensions = chromiumRules(profile);
    assertThat(extensions).hasSize(5);
    final var normalized = (ObjectNode) profile.deepCopy();
    final var baseRules = new JsonMapper().createArrayNode();
    for (final var rule : profile.path("syscalls")) {
      if (!extensions.contains(rule)) {
        baseRules.add(rule);
      }
    }
    normalized.set("syscalls", baseRules);

    final var digest =
        MessageDigest.getInstance("SHA-256")
            .digest(normalized.toString().getBytes(StandardCharsets.UTF_8));

    assertThat(HexFormat.of().formatHex(digest))
        .isEqualTo("afb4934b023cfceaaec1a9d752ca3f801aaa96eb2e59abe6e7ea16976948e080");
  }

  @Test
  void keepsDefaultDenyAndLimitsChromiumExceptionsToSupportedArchitectures() throws Exception {
    final var profile = profile();
    final var extensions = chromiumRules(profile);

    assertThat(profile.path("defaultAction").asString()).isEqualTo("SCMP_ACT_ERRNO");
    assertThat(profile.path("defaultErrnoRet").asInt()).isEqualTo(1);
    assertThat(extensions).hasSize(5);
    for (final var rule : extensions) {
      assertThat(rule.path("action").asString()).isEqualTo("SCMP_ACT_ALLOW");
      assertThat(rule.path("names").size()).isEqualTo(1);
      assertThat(rule.path("names").get(0).asString()).isIn("clone", "unshare", "chroot");
      assertThat(rule.path("includes").size()).isEqualTo(1);
      assertThat(rule.at("/includes/arches").toString()).isEqualTo("[\"amd64\",\"arm64\"]");
      assertThat(rule.has("excludes")).isFalse();
    }
  }

  @Test
  void permitsOnlyTheExactNamespaceFlagsUsedByChromium() throws Exception {
    final var extensions = chromiumRules(profile());
    final var cloneRules = rulesNamed(extensions, "clone");
    final var unshareRules = rulesNamed(extensions, "unshare");

    assertThat(cloneRules)
        .extracting(rule -> rule.at("/args/0/value").asInt())
        .containsExactlyInAnyOrder(268435473, 1879048209, 536870929);
    assertThat(unshareRules).hasSize(1);
    assertThat(unshareRules.getFirst().at("/args/0/value").asInt()).isEqualTo(268435456);
    for (final var rule : extensions) {
      if (!rule.path("names").get(0).asString().equals("chroot")) {
        assertThat(rule.path("args").size()).isEqualTo(1);
        final var argument = rule.path("args").get(0);
        assertThat(argument.size()).isEqualTo(3);
        assertThat(argument.path("index").asInt()).isZero();
        assertThat(argument.path("op").asString()).isEqualTo("SCMP_CMP_EQ");
      }
    }
    final var chrootRules = rulesNamed(extensions, "chroot");
    assertThat(chrootRules).hasSize(1);
    assertThat(chrootRules.getFirst().has("args")).isFalse();
  }

  private JsonNode profile() throws Exception {
    return new JsonMapper().readTree(Files.readString(Path.of("docker/chromium-seccomp.json")));
  }

  private List<JsonNode> chromiumRules(final JsonNode profile) {
    return StreamSupport.stream(profile.path("syscalls").spliterator(), false)
        .filter(rule -> rule.has("comment") && rule.path("comment").asString().startsWith("Bemol:"))
        .toList();
  }

  private List<JsonNode> rulesNamed(final List<JsonNode> rules, final String name) {
    return rules.stream()
        .filter(rule -> rule.path("names").get(0).asString().equals(name))
        .toList();
  }
}
