package ar.edu.utn.frvm.typeit.bemol_api.audit.interfaces;

import ar.edu.utn.frvm.typeit.bemol_api.audit.entities.AuditEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AuditEventRepository
    extends JpaRepository<AuditEvent, UUID>,
        JpaSpecificationExecutor<AuditEvent>,
        AuditEventOptionsQueries {}
