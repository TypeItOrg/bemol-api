package ar.edu.utn.frvm.typeit.bemol_api.authorization.security;

import static ar.edu.utn.frvm.typeit.bemol_api.support.AuthTestData.institutionalPrincipal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ar.edu.utn.frvm.typeit.bemol_api.authorization.RequiresAnyPermission;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.RequiresPermission;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionCode;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.AuthorityResolver;
import ar.edu.utn.frvm.typeit.bemol_api.authorization.services.AuthorizationService;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.services.OperatingContextResolver;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.mockito.Answers;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tests the real permission aspect on every annotated controller method. Business flows are
 * separate.
 */
class PermissionControllerMatrixTest {
  @TestFactory
  Stream<DynamicTest> everyControllerPermissionRejectsAllUnrelatedGrants() {
    final var scanner = new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
    return scanner.findCandidateComponents("ar.edu.utn.frvm.typeit.bemol_api").stream()
        .map(
            bean ->
                ClassUtils.resolveClassName(
                    Objects.requireNonNull(bean.getBeanClassName()), getClass().getClassLoader()))
        .flatMap(type -> Arrays.stream(type.getDeclaredMethods()))
        .filter(
            method ->
                method.isAnnotationPresent(RequiresPermission.class)
                    || method.isAnnotationPresent(RequiresAnyPermission.class))
        .map(
            method ->
                DynamicTest.dynamicTest(
                    method.getDeclaringClass().getSimpleName() + "." + method.getName(),
                    () -> checkBoundary(method)));
  }

  private void checkBoundary(final Method method) throws Throwable {
    final var principal = institutionalPrincipal(UUID.randomUUID(), UUID.randomUUID());
    final var resolver = mock(AuthorityResolver.class);
    final var grants = new AtomicReference<Set<PermissionCode>>(Set.of());
    when(resolver.resolveForPerson(principal.personId(), principal.institutionId()))
        .thenAnswer(invocation -> grants.get());
    final var service = new AuthorizationService(resolver, mock(OperatingContextResolver.class));
    final var calls = new AtomicInteger();
    final var target =
        mock(
            method.getDeclaringClass(),
            invocation -> {
              if (invocation.getMethod().equals(method)) {
                calls.incrementAndGet();
              }
              return Answers.RETURNS_DEFAULTS.answer(invocation);
            });
    final var factory = new AspectJProxyFactory(target);
    factory.setProxyTargetClass(true);
    factory.addAspect(new PermissionAuthorizationAspect(service));
    final Object secured = factory.getProxy();
    final var required = method.getAnnotation(RequiresPermission.class);
    final Set<PermissionCode> accepted =
        required == null
            ? Set.of(method.getAnnotation(RequiresAnyPermission.class).value())
            : Set.of(required.value());
    final var arguments =
        Arrays.stream(method.getParameterTypes()).map(this::defaultArgument).toArray();

    SecurityContextHolder.getContext()
        .setAuthentication(new TestingAuthenticationToken(principal, ""));
    try {
      assertThatThrownBy(() -> invoke(method, secured, arguments))
          .isInstanceOf(AccessDeniedException.class);
      assertThat(calls).hasValue(0);

      for (final var permission : PermissionCode.values()) {
        grants.set(Set.of(permission));
        final int before = calls.get();
        if (accepted.contains(permission)) {
          invoke(method, secured, arguments);
          assertThat(calls).hasValue(before + 1);
        } else {
          assertThatThrownBy(() -> invoke(method, secured, arguments))
              .as("%s must not accept %s", method.getName(), permission)
              .isInstanceOf(AccessDeniedException.class);
          assertThat(calls).hasValue(before);
        }
      }

      SecurityContextHolder.clearContext();
      assertThatThrownBy(() -> invoke(method, secured, arguments))
          .isInstanceOf(AccessDeniedException.class);
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  private void invoke(Method method, Object target, @Nullable Object[] arguments) throws Throwable {
    try {
      method.invoke(target, arguments);
    } catch (InvocationTargetException exception) {
      throw exception.getCause();
    }
  }

  private @Nullable Object defaultArgument(Class<?> type) {
    if (type == boolean.class) {
      return false;
    }
    if (type == int.class) {
      return 0;
    }
    if (type == long.class) {
      return 0L;
    }
    return null;
  }
}
