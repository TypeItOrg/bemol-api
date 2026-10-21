package ar.edu.utn.frvm.typeit.bemol_api.authorization.entities;

import ar.edu.utn.frvm.typeit.bemol_api.authorization.enums.PermissionScope;
import ar.edu.utn.frvm.typeit.bemol_api.common.persistence.Auditable;
import ar.edu.utn.frvm.typeit.bemol_api.common.persistence.GeneratedUUIDv7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "permissions",
    uniqueConstraints = @UniqueConstraint(name = "permissions_code_unique", columnNames = "code"))
@Getter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Permission extends Auditable {

  @Id
  @GeneratedUUIDv7
  @Column(name = "permission_id")
  private UUID id;

  @Column(nullable = false, length = 100)
  private String code;

  @Column(nullable = false, length = 255)
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private PermissionScope scope;
}
