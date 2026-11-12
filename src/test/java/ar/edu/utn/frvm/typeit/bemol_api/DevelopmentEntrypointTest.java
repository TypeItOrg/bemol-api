package ar.edu.utn.frvm.typeit.bemol_api;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DevelopmentEntrypointTest {
  @TempDir Path temporary;

  @Test
  void rootPreparesOnlyApplicationVolumesWithoutFollowingLinksAndDropsPrivileges()
      throws Exception {
    final var binaryDirectory = Files.createDirectory(temporary.resolve("bin"));
    final var calls = temporary.resolve("calls");
    for (final var command : List.of("id", "mkdir", "chown", "runuser")) {
      final var binary = binaryDirectory.resolve(command);
      final String body =
          command.equals("id")
              ? "echo 0\n"
              : "printf '%s\\n' \"" + command + " $*\" >> '" + calls + "'\n";
      Files.writeString(binary, "#!/bin/sh\nset -eu\n" + body);
      assertThat(binary.toFile().setExecutable(true, true)).isTrue();
    }
    final var script = Path.of("docker/dev-entrypoint.sh").toAbsolutePath();
    final var builder = new ProcessBuilder("/bin/sh", script.toString());
    builder.environment().put("PATH", binaryDirectory + ":/usr/bin:/bin");
    final var process = builder.start();

    try {
      assertThat(process.waitFor(2, TimeUnit.SECONDS)).isTrue();
      assertThat(process.exitValue()).isZero();
      assertThat(Files.readAllLines(calls))
          .containsExactly(
              "mkdir -p /home/gradle/.gradle",
              "chown -R -h -P gradle:gradle -- /home/gradle/.gradle",
              "runuser -u gradle -- " + script);
    } finally {
      process.destroyForcibly();
    }
  }
}
