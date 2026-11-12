package ar.edu.utn.frvm.typeit.bemol_api.audit.definition;

public record AuditView(AuditFeed feed, boolean institutional) {
  public static AuditView platform(final AuditFeed feed) {
    return new AuditView(feed, false);
  }

  public static AuditView institution(final AuditFeed feed) {
    return new AuditView(feed, true);
  }

  public boolean shows(final AuditOperation operation) {
    final boolean sameFeed = operation.feed() == feed;
    final boolean hiddenFromInstitution = institutional && operation.platformOnly();

    return sameFeed && !hiddenFromInstitution;
  }
}
