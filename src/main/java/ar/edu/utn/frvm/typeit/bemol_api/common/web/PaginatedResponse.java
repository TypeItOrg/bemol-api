package ar.edu.utn.frvm.typeit.bemol_api.common.web;

import java.util.List;
import lombok.Builder;
import org.springframework.data.domain.Page;

@Builder
public record PaginatedResponse<T>(
    List<T> items, int page, int size, long totalItems, int totalPages) {

  public static <T> PaginatedResponse<T> from(Page<T> page) {
    return PaginatedResponse.<T>builder()
        .items(page.getContent())
        .page(page.getNumber())
        .size(page.getSize())
        .totalItems(page.getTotalElements())
        .totalPages(page.getTotalPages())
        .build();
  }
}
