package ar.edu.utn.frvm.typeit.bemol_api.enrollment.interfaces;

import ar.edu.utn.frvm.typeit.bemol_api.enrollment.entities.EnrollmentPdfSettings;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnrollmentPdfSettingsRepository
    extends JpaRepository<EnrollmentPdfSettings, UUID> {}
