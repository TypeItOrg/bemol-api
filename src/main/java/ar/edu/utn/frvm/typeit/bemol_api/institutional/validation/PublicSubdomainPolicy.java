package ar.edu.utn.frvm.typeit.bemol_api.institutional.validation;

import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.InstitutionMessages;
import ar.edu.utn.frvm.typeit.bemol_api.institutional.exceptions.InvalidPublicSubdomainException;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

public final class PublicSubdomainPolicy {
  private static final Pattern DNS_LABEL = Pattern.compile("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?");
  private static final Set<String> RESERVED =
      Set.of("www", "api", "admin", "auth", "test", "testing", "qa", "staging", "localhost");

  private PublicSubdomainPolicy() {}

  public static void validate(final @Nullable String name) {
    if (name == null) {
      return;
    }
    if (!DNS_LABEL.matcher(name).matches()) {
      throw new InvalidPublicSubdomainException();
    }
    if (RESERVED.contains(name)) {
      throw new InvalidPublicSubdomainException(InstitutionMessages.PUBLIC_SUBDOMAIN_RESERVED);
    }
  }
}
