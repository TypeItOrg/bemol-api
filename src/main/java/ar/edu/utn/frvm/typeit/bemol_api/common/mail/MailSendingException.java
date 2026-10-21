package ar.edu.utn.frvm.typeit.bemol_api.common.mail;

import java.io.Serial;

public final class MailSendingException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  public MailSendingException(final Throwable cause) {
    super(MailMessages.SEND_FAILED, cause);
  }
}
