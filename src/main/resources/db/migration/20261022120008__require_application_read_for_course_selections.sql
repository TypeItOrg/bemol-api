-- Reading embedded course selections also needs access to their application detail.
INSERT INTO role_permissions (permission_id, role_id)
SELECT required_permission.permission_id, granted.role_id
FROM role_permissions granted
JOIN permissions permission ON permission.permission_id = granted.permission_id
JOIN permissions required_permission
    ON required_permission.code = 'institution:enrollment-application:read'
WHERE permission.code = 'institution:enrollment-application-course:read'
ON CONFLICT (permission_id, role_id) DO NOTHING;
