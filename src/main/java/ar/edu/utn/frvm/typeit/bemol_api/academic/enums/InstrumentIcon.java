package ar.edu.utn.frvm.typeit.bemol_api.academic.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Visual family chosen per instrument; never inferred from the instrument name. */
@Getter
@RequiredArgsConstructor
public enum InstrumentIcon {
  MUSIC("Música"),
  PIANO("Piano"),
  KEYBOARD("Teclado"),
  GUITAR("Guitarra"),
  PERCUSSION("Percusión"),
  VOICE("Voz"),
  VIOLIN("Violín"),
  CELLO("Violonchelo"),
  HARP("Arpa"),
  FLUTE("Flauta"),
  CLARINET("Clarinete"),
  SAXOPHONE("Saxofón"),
  TRUMPET("Trompeta"),
  BANDONEON("Bandoneón");

  private final String label;
}
