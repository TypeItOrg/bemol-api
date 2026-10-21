package ar.edu.utn.frvm.typeit.bemol_api.academic.services;

import ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions.PrerequisiteCycleException;
import ar.edu.utn.frvm.typeit.bemol_api.academic.interfaces.PrerequisiteRepository;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class PrerequisiteCycleValidator {

  private final PrerequisiteRepository prerequisiteRepository;

  void validate(
      final UUID studyPlanId,
      final UUID targetId,
      final UUID requiredId,
      final @Nullable UUID excludedPrerequisiteId) {
    final Map<UUID, Set<UUID>> requiredBySpace = new HashMap<>();
    for (final var prerequisite : prerequisiteRepository.findByStudyPlan_Id(studyPlanId)) {
      if (prerequisite.getId().equals(excludedPrerequisiteId)) {
        continue;
      }
      requiredBySpace
          .computeIfAbsent(
              prerequisite.getTargetStudyPlanSpace().getId(), ignored -> new HashSet<>())
          .add(prerequisite.getRequiredStudyPlanSpace().getId());
    }
    final Set<UUID> visited = new HashSet<>();
    final Deque<UUID> pending = new ArrayDeque<>();
    pending.add(requiredId);
    while (!pending.isEmpty()) {
      final UUID current = pending.removeFirst();
      if (!visited.add(current)) {
        continue;
      }
      if (current.equals(targetId)) {
        throw new PrerequisiteCycleException();
      }
      pending.addAll(requiredBySpace.getOrDefault(current, Set.of()));
    }
  }
}
