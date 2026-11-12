package ar.edu.utn.frvm.typeit.bemol_api.audit.services;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

final class AuditPages {
  private static final int MAX_PAGE_SIZE = 50;
  private static final Sort RECENT_FIRST = Sort.by(Sort.Direction.DESC, "occurredAt", "id");

  private AuditPages() {}

  static PageRequest bounded(final Pageable pageable) {
    final int size = Math.min(pageable.getPageSize(), MAX_PAGE_SIZE);
    return PageRequest.of(pageable.getPageNumber(), size);
  }

  static PageRequest recentFirst(final Pageable pageable) {
    return bounded(pageable).withSort(RECENT_FIRST);
  }
}
