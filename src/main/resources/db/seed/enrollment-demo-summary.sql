-- Report current state rather than promise the original state after a rerun.
SELECT m.document AS documento, m.first_name || ' ' || m.last_name AS persona,
       f.scenario AS caso, a.status AS solicitud,
       (SELECT count(*) FROM enrollment_document_requirements r
        WHERE r.application_id = a.enrollment_application_id AND NOT EXISTS (
            SELECT 1 FROM enrollment_attachments d WHERE d.requirement_id = r.id
              AND d.version_status = 'CURRENT' AND d.deleted_at IS NULL)) AS documentos_sin_entregar,
       (SELECT string_agg(DISTINCT s.status, ', ' ORDER BY s.status)
        FROM enrollment_application_courses s WHERE s.enrollment_application_id = a.enrollment_application_id) AS cursos
FROM seed_applications f JOIN seed_members m ON m.number = f.member_number
JOIN seed_context c ON true
JOIN enrollment_applications a ON a.enrollment_application_id =
     pg_temp.demo_id('application:' || f.scenario || ':' || c.academic_year)
ORDER BY m.number;
