package ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
    requiredProperties = {
      "pendingChanges",
      "affectedStudents",
      "newGrades",
      "modifiedGrades",
      "deletedGrades"
    })
public record PendingGradesSummaryResponse(
    long pendingChanges,
    long affectedStudents,
    long newGrades,
    long modifiedGrades,
    long deletedGrades) {}
