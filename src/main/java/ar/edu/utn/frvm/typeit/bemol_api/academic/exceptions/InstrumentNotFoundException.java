package ar.edu.utn.frvm.typeit.bemol_api.academic.exceptions;

import java.io.Serial;

public class InstrumentNotFoundException extends AcademicNotFoundException {

  @Serial private static final long serialVersionUID = 1L;

  public InstrumentNotFoundException() {
    super(AcademicMessages.INSTRUMENT_NOT_FOUND);
  }
}
