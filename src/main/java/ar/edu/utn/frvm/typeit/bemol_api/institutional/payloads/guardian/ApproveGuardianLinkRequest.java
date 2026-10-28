package ar.edu.utn.frvm.typeit.bemol_api.institutional.payloads.guardian;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record ApproveGuardianLinkRequest(@Nullable UUID confirmedPersonId) {}
