-- Preserve student roles whose permissions differ from the previous defaults.
WITH default_student_roles AS (
    SELECT role.role_id
    FROM roles role
    JOIN role_permissions assigned ON assigned.role_id = role.role_id
    JOIN permissions permission ON permission.permission_id = assigned.permission_id
    WHERE role.scope = 'INSTITUTION'
      AND role.code = 'STUDENT'
      AND role.is_system = true
      AND role.institution_id IS NOT NULL
    GROUP BY role.role_id
    HAVING bool_and(permission.code IN ('institution:study-plan:read', 'institution:academic-year:read'))
       AND bool_or(permission.code = 'institution:study-plan:read')
), removed_catalog_permissions AS (
    DELETE FROM role_permissions assigned
    USING default_student_roles role, permissions permission
    WHERE assigned.role_id = role.role_id
      AND assigned.permission_id = permission.permission_id
      AND permission.code IN ('institution:study-plan:read', 'institution:academic-year:read')
    RETURNING assigned.role_id
)
INSERT INTO role_permissions (permission_id, role_id)
SELECT DISTINCT permission.permission_id, removed.role_id
FROM removed_catalog_permissions removed
CROSS JOIN permissions permission
WHERE permission.code = 'institution:academic-offer:read'
ON CONFLICT (permission_id, role_id) DO NOTHING;
