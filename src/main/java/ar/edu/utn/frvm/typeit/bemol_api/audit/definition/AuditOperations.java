package ar.edu.utn.frvm.typeit.bemol_api.audit.definition;

import ar.edu.utn.frvm.typeit.bemol_api.audit.exceptions.AuditMessages;
import ar.edu.utn.frvm.typeit.bemol_api.audit.exceptions.AuditValidationException;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
public class AuditOperations {
  private static final int MAX_OPERATION_LENGTH = 60;
  private static final int MAX_RESOURCE_LENGTH = 40;

  private final Map<String, AuditOperation> operations;
  private final Map<String, AuditFeed> moduleFeeds;
  private final Map<String, AuditResource> resources;

  public AuditOperations(final List<AuditOperationsProvider> providers) {
    final var definitions = new LinkedHashMap<String, AuditOperation>();
    final var resourceTypes = new LinkedHashMap<String, AuditResource>();

    for (final var provider : providers) {
      provider.resources().forEach(resource -> register(resourceTypes, resource));
      provider.operations().forEach(operation -> register(definitions, operation));
    }

    operations = Map.copyOf(definitions);
    moduleFeeds = moduleFeeds(definitions.values());
    resources = Map.copyOf(resourceTypes);
  }

  public @Nullable AuditOperation find(final String code) {
    return operations.get(code);
  }

  public List<AuditOperation> visible(final AuditView view) {
    return operations.values().stream().filter(view::shows).toList();
  }

  public List<String> visibleCodes(final AuditView view) {
    return visible(view).stream().map(entry -> entry.name()).toList();
  }

  public List<String> hiddenCodes(final AuditView view) {
    return operations.values().stream()
        .filter(operation -> !view.shows(operation))
        .map(entry -> entry.name())
        .toList();
  }

  public List<String> moduleCodes(final String module) {
    return operations.values().stream()
        .filter(operation -> operation.getModule().code().equals(module))
        .map(entry -> entry.name())
        .toList();
  }

  public List<AuditModuleDefinition> modules(final AuditView view) {
    return visible(view).stream()
        .map(entry -> entry.getModule())
        .distinct()
        .sorted(Comparator.comparing(entry -> entry.code()))
        .toList();
  }

  public AuditFeed feedOf(final @Nullable String module) {
    if (module == null) {
      return AuditFeed.ACTIVITY;
    }

    final var feed = moduleFeeds.get(module);
    if (feed == null) {
      throw new AuditValidationException(AuditMessages.INVALID_OPERATION_FILTER);
    }
    return feed;
  }

  public void validate(
      final AuditView view, final @Nullable String action, final @Nullable String module) {
    final boolean hiddenAction = action != null && !showsAction(view, action);
    final boolean hiddenModule = module != null && !showsModule(view, module);

    if (hiddenAction || hiddenModule) {
      throw new AuditValidationException(AuditMessages.INVALID_OPERATION_FILTER);
    }
  }

  public Map<String, String> resourceLabels() {
    return resources.values().stream()
        .collect(Collectors.toUnmodifiableMap(entry -> entry.name(), entry -> entry.label()));
  }

  public String resourceLabel(final String code) {
    final var resource = resources.get(code);
    return resource == null ? code : resource.label();
  }

  public void validateResource(final @Nullable String code) {
    if (code != null && !resources.containsKey(code)) {
      throw new AuditValidationException(AuditMessages.INVALID_RESOURCE_FILTER);
    }
  }

  private boolean showsAction(final AuditView view, final String action) {
    final var operation = operations.get(action);
    return operation != null && view.shows(operation);
  }

  private boolean showsModule(final AuditView view, final String module) {
    return visible(view).stream()
        .anyMatch(operation -> operation.getModule().code().equals(module));
  }

  private static void register(
      final Map<String, AuditResource> resources, final AuditResource resource) {
    final boolean tooLong = resource.name().length() > MAX_RESOURCE_LENGTH;
    final boolean duplicate = resources.putIfAbsent(resource.name(), resource) != null;

    if (tooLong || duplicate) {
      throw new IllegalStateException("Duplicate or invalid audit resource: " + resource.name());
    }
  }

  private static void register(
      final Map<String, AuditOperation> operations, final AuditOperation operation) {
    final boolean tooLong = operation.name().length() > MAX_OPERATION_LENGTH;
    final boolean duplicate = operations.putIfAbsent(operation.name(), operation) != null;

    if (tooLong || duplicate) {
      throw new IllegalStateException("Duplicate or invalid audit operation: " + operation.name());
    }
  }

  private static Map<String, AuditFeed> moduleFeeds(final Collection<AuditOperation> operations) {
    final var modules = new LinkedHashMap<String, AuditOperation>();

    for (final var operation : operations) {
      final var module = operation.getModule();
      final var previous = modules.putIfAbsent(module.code(), operation);

      if (previous != null && conflicts(previous, operation)) {
        throw new IllegalStateException("Conflicting audit module: " + module.code());
      }
    }

    return modules.entrySet().stream()
        .collect(
            Collectors.toUnmodifiableMap(
                entry -> entry.getKey(), entry -> entry.getValue().feed()));
  }

  private static boolean conflicts(final AuditOperation previous, final AuditOperation operation) {
    final boolean differentModule = !previous.getModule().equals(operation.getModule());
    final boolean differentFeed = previous.feed() != operation.feed();

    return differentModule || differentFeed;
  }
}
