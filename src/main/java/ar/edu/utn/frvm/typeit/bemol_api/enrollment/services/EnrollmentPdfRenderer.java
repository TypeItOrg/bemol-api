package ar.edu.utn.frvm.typeit.bemol_api.enrollment.services;

import ar.edu.utn.frvm.typeit.bemol_api.enrollment.payloads.EnrollmentPdfData;

public interface EnrollmentPdfRenderer {
  byte[] render(EnrollmentPdfData data);
}
