-- Keep the demo workspace navigation aligned with the AI Deal Desk showcase flow.
INSERT INTO sys_module (id, organization_id, module_key, enable, pos, create_user, create_time, update_user, update_time)
SELECT UUID_SHORT(), '100001', 'aiAssistant', true, 2, 'admin', UNIX_TIMESTAMP() * 1000, 'admin', UNIX_TIMESTAMP() * 1000
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_module
    WHERE organization_id = '100001'
      AND module_key = 'aiAssistant'
);

UPDATE sys_module
SET pos = CASE module_key
              WHEN 'home' THEN 1
              WHEN 'aiAssistant' THEN 2
              WHEN 'agent' THEN 3
              WHEN 'clue' THEN 4
              WHEN 'customer' THEN 5
              WHEN 'business' THEN 6
              WHEN 'contract' THEN 7
              WHEN 'order' THEN 8
              WHEN 'product' THEN 9
              WHEN 'dashboard' THEN 10
              WHEN 'tender' THEN 11
              WHEN 'customForm' THEN 12
              WHEN 'setting' THEN 13
              ELSE pos
          END,
    update_user = 'admin',
    update_time = UNIX_TIMESTAMP() * 1000
WHERE organization_id = '100001'
  AND module_key IN (
      'home',
      'aiAssistant',
      'agent',
      'clue',
      'customer',
      'business',
      'contract',
      'order',
      'product',
      'dashboard',
      'tender',
      'customForm',
      'setting'
  );
