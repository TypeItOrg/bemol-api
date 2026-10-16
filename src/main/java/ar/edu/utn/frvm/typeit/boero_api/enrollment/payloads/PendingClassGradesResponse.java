package ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(
    requiredProperties = {
      "courseId",
      "courseName",
      "classId",
      "classLabel",
      "pendingChanges",
      "affectedStudents"
    })
public record PendingClassGradesResponse(
    UUID courseId,
    String courseName,
    UUID classId,
    String classLabel,
    long pendingChanges,
    long affectedStudents) {}
