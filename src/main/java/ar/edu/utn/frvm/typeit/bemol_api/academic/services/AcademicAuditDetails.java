package ar.edu.utn.frvm.typeit.bemol_api.academic.services;

import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.AcademicLevel;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.Course;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.Prerequisite;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.StudyPlan;
import ar.edu.utn.frvm.typeit.bemol_api.academic.entities.StudyPlanSpace;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.CourseClassDayResponse;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.CourseClassResponse;
import ar.edu.utn.frvm.typeit.bemol_api.academic.payloads.CourseClassScheduleResponse;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

final class AcademicAuditDetails {
  private AcademicAuditDetails() {}

  static Map<String, Object> level(final AcademicLevel level) {
    return Map.of("name", level.getName(), "displayOrder", level.getDisplayOrder());
  }

  static Map<String, Object> plan(final StudyPlan plan) {
    final Map<String, Object> result = new LinkedHashMap<>();
    result.put("name", plan.getName());
    result.put("status", plan.getStatus().name());
    result.put("versionNumber", plan.getVersionNumber());
    result.put(
        "effectiveFrom",
        plan.getEffectiveFrom() == null ? null : plan.getEffectiveFrom().toString());
    result.put(
        "effectiveTo", plan.getEffectiveTo() == null ? null : plan.getEffectiveTo().toString());
    return result;
  }

  static Map<String, Object> space(final StudyPlanSpace space, final List<UUID> instrumentIds) {
    final Map<String, Object> result = new LinkedHashMap<>();
    result.put("academicSpaceId", space.getAcademicSpace().getId().toString());
    result.put("name", space.getAcademicSpace().getName());
    result.put(
        "academicLevelId",
        space.getAcademicLevel() == null ? null : space.getAcademicLevel().getId().toString());
    result.put(
        "academicLevelName",
        space.getAcademicLevel() == null ? null : space.getAcademicLevel().getName());
    result.put("requirementType", space.getRequirementType().name());
    result.put("displayOrder", space.getDisplayOrder());
    result.put("approvalMode", space.getApprovalMode().name());
    result.put("instrumentIds", instrumentIds.stream().map(id -> id.toString()).sorted().toList());
    result.put("instrumentCount", instrumentIds.size());
    return result;
  }

  static Map<String, Object> prerequisite(final Prerequisite prerequisite) {
    return Map.of(
        "requiredSpaceId", prerequisite.getRequiredStudyPlanSpace().getId().toString(),
        "requiredSpaceName", prerequisite.getRequiredStudyPlanSpace().getAcademicSpace().getName(),
        "targetSpaceName", prerequisite.getTargetStudyPlanSpace().getAcademicSpace().getName(),
        "requirementStage", prerequisite.getRequirementStage().name(),
        "requiredCondition", prerequisite.getRequiredCondition().name());
  }

  static String prerequisiteName(final Prerequisite prerequisite) {
    return prerequisite.getRequiredStudyPlanSpace().getAcademicSpace().getName()
        + " → "
        + prerequisite.getTargetStudyPlanSpace().getAcademicSpace().getName();
  }

  static String courseName(final Course course) {
    return course.getAcademicSpace().getName()
        + " — "
        + course.getStudyPlan().getName()
        + " ("
        + course.getAcademicYear().getYear()
        + ")";
  }

  static Map<String, Object> course(final Course course, final List<CourseClassResponse> classes) {
    return Map.of(
        "status", course.getStatus().name(),
        "classCount", classes.size(),
        "teacherCount", teacherIds(classes).size(),
        "dayCount", classes.stream().mapToInt(item -> item.days().size()).sum(),
        "scheduleCount",
            classes.stream()
                .flatMap(item -> item.days().stream())
                .mapToInt(day -> day.schedules().size())
                .sum());
  }

  static List<String> teacherIds(final List<CourseClassResponse> classes) {
    return classes.stream()
        .flatMap(item -> item.teachers().stream())
        .map(teacher -> teacher.personId().toString())
        .distinct()
        .sorted()
        .toList();
  }

  static List<String> relatedTeachers(
      final List<CourseClassResponse> before, final List<CourseClassResponse> after) {
    return Stream.concat(teacherIds(before).stream(), teacherIds(after).stream())
        .distinct()
        .sorted()
        .toList();
  }

  static Map<String, Object> change(
      final String entityName, final Map<String, Object> before, final Map<String, Object> after) {
    return Map.of(
        "entityName",
        entityName,
        "before",
        before,
        "after",
        after,
        "changedFields",
        Stream.concat(before.keySet().stream(), after.keySet().stream())
            .distinct()
            .filter(key -> !java.util.Objects.equals(before.get(key), after.get(key)))
            .toList());
  }

  static List<CourseClassState> classState(final List<CourseClassResponse> classes) {
    return classes.stream()
        .map(
            item ->
                new CourseClassState(
                    item.classNumber(),
                    item.teachers().stream().map(teacher -> teacher.personId()).sorted().toList(),
                    item.days().stream()
                        .map(
                            day ->
                                new CourseClassDayResponse(
                                    day.dayOfWeek(),
                                    day.capacity(),
                                    day.periodDurationMinutes(),
                                    day.schedules().stream()
                                        .sorted(
                                            Comparator.comparing(
                                                    (CourseClassScheduleResponse schedule) ->
                                                        schedule.startTime())
                                                .thenComparing(schedule -> schedule.endTime()))
                                        .toList()))
                        .sorted(Comparator.comparing(day -> day.dayOfWeek()))
                        .toList()))
        .toList();
  }

  private record CourseClassState(
      int classNumber, List<UUID> teacherIds, List<CourseClassDayResponse> days) {}
}
