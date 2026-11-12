package ar.edu.utn.frvm.typeit.bemol_api.academic.services;

import static ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.AcademicMessages.ACADEMIC_LIFECYCLE_ACTOR_NOT_FOUND;

import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.CurrentPrincipal;
import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedPlatformAccount;
import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtAuthenticatedUser;
import ar.edu.utn.frvm.typeit.bemol_api.auth.filters.JwtPrincipal;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
class AcademicLifecycleActorResolver {

  AcademicLifecycleActor resolve() {
    return CurrentPrincipal.jwt()
        .map(AcademicLifecycleActorResolver::actor)
        .orElseThrow(() -> new AccessDeniedException(ACADEMIC_LIFECYCLE_ACTOR_NOT_FOUND));
  }

  private static AcademicLifecycleActor actor(final JwtPrincipal principal) {
    return switch (principal) {
      case JwtAuthenticatedUser user ->
          new AcademicLifecycleActor(user.accountType(), user.actorUserId());
      case JwtAuthenticatedPlatformAccount platform ->
          new AcademicLifecycleActor(platform.accountType(), platform.platformAccountId());
    };
  }
}
