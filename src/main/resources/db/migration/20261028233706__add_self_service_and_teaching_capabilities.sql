INSERT INTO permissions (created_at, updated_at, permission_id, scope, code, description)
SELECT CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, md5(code)::uuid, 'INSTITUTION', code, description
FROM (VALUES
    ('institution:own-enrollment-application:read', 'Consultar mis solicitudes de inscripción'),
    ('institution:own-enrollment-application:manage', 'Crear, modificar y enviar mis solicitudes de inscripción'),
    ('institution:own-formation:read', 'Consultar mis materias y horarios'),
    ('institution:teaching:read', 'Consultar mis clases, horarios y estudiantes'),
    ('institution:teaching:academic-status-update', 'Modificar resultados académicos de mis clases')
) AS definitions(code, description)
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (permission_id, role_id)
SELECT permission.permission_id, role.role_id
FROM permissions permission
JOIN roles role ON role.scope = 'INSTITUTION' AND role.is_system = true
WHERE permission.code IN ('institution:own-enrollment-application:read', 'institution:own-enrollment-application:manage', 'institution:own-formation:read', 'institution:teaching:read', 'institution:teaching:academic-status-update')
  AND (role.code = 'INSTITUTIONAL_AUTHORITY'
       OR (role.code IN ('APPLICANT', 'STUDENT') AND permission.code = 'institution:own-enrollment-application:read')
       OR (role.code IN ('APPLICANT', 'STUDENT') AND permission.code = 'institution:own-enrollment-application:manage')
       OR (role.code = 'STUDENT' AND permission.code = 'institution:own-formation:read')
       OR (role.code = 'TEACHER' AND permission.code IN ('institution:teaching:read', 'institution:teaching:academic-status-update')))
ON CONFLICT (permission_id, role_id) DO NOTHING;
