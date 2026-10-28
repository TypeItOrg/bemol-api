package ar.edu.utn.frvm.typeit.bemol_api.auth.services;

import ar.edu.utn.frvm.typeit.bemol_api.auth.interfaces.PersonAccessInvitationRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InvitationMailOutcomeRecorder {
  private final PersonAccessInvitationRepository invitations;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(final UUID id, final String outcome) {
    invitations.findById(id).ifPresent(invitation -> invitation.recordMailOutcome(outcome));
  }
}
