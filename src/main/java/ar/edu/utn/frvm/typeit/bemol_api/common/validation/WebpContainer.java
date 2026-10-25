package ar.edu.utn.frvm.typeit.bemol_api.common.validation;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class WebpContainer {
  private WebpContainer() {}

  public static boolean isComplete(final byte[] bytes) {
    return bytes.length >= 12
        && Integer.toUnsignedLong(ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt(4))
            == bytes.length - 8L;
  }
}
