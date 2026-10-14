package ar.edu.utn.frvm.typeit.boero_api.auth.services;

import java.util.Optional;

public final class RefreshRotationPolicy {

  private RefreshRotationPolicy() {}

  public static RefreshRotationDecision decide(
      final boolean revoked, final boolean expired, final Optional<RefreshReplay> replay) {
    if (revoked && replay.isPresent()) {
      return new RefreshRotationDecision.Replay(replay.orElseThrow());
    }

    if (revoked) {
      return new RefreshRotationDecision.Reuse();
    }
    if (expired) {
      return new RefreshRotationDecision.Invalid();
    }

    return new RefreshRotationDecision.Rotate();
  }
}
