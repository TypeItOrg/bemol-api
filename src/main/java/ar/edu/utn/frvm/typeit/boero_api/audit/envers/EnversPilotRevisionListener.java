package ar.edu.utn.frvm.typeit.boero_api.audit.envers;

import ar.edu.utn.frvm.typeit.boero_api.audit.enums.AuditActorKind;
import ar.edu.utn.frvm.typeit.boero_api.auth.filters.JwtAuthenticatedPlatformAccount;
import ar.edu.utn.frvm.typeit.boero_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.boero_api.common.logging.RequestLoggingFilter;
import org.hibernate.envers.RevisionListener;
import org.slf4j.MDC;
import org.springframework.security.core.context.SecurityContextHolder;

public class EnversPilotRevisionListener implements RevisionListener {
  @Override
  public void newRevision(final Object revisionEntity) {
    final var revision = (EnversPilotRevision) revisionEntity;
    revision.identifyTransaction(EnversAuditTransaction.currentId());
    final var authentication = SecurityContextHolder.getContext().getAuthentication();
    final Object principal = authentication == null ? null : authentication.getPrincipal();
    final var requestId = MDC.get(RequestLoggingFilter.MDC_REQUEST_ID_KEY);
    if (principal instanceof JwtAuthenticatedUser user) {
      revision.identifyActor(AuditActorKind.INSTITUTION, user.personId(), null, requestId);
    } else if (principal instanceof JwtAuthenticatedPlatformAccount account) {
      revision.identifyActor(AuditActorKind.PLATFORM, null, account.platformAccountId(), requestId);
    } else {
      revision.identifyActor(AuditActorKind.ANONYMOUS, null, null, requestId);
    }
  }
}
