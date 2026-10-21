package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

public sealed interface RefreshRotationDecision
    permits RefreshRotationDecision.Rotate,
        RefreshRotationDecision.Replay,
        RefreshRotationDecision.Invalid,
        RefreshRotationDecision.Reuse {

  record Rotate() implements RefreshRotationDecision {}

  record Replay(RefreshReplay tokens) implements RefreshRotationDecision {}

  record Invalid() implements RefreshRotationDecision {}

  record Reuse() implements RefreshRotationDecision {}
}
