package ar.edu.utn.frvm.typeit.bemol_api.academic.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RequirementStage {
  TO_ENROLL("Para cursar"),
  TO_PASS("Para aprobar");

  private final String label;
}
