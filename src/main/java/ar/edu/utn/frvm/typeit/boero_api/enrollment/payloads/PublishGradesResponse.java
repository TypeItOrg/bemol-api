package ar.edu.utn.frvm.typeit.boero_api.enrollment.payloads;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = {"publishedGrades", "deletedGrades", "affectedStudents"})
public record PublishGradesResponse(
    long publishedGrades, long deletedGrades, long affectedStudents) {}
