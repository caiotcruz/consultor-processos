
UPDATE parser_versions
SET    active      = true,
       released_at = NOW(),
       released_by = 'system-phase-7-fix'
WHERE  version = '1.0.0'
  AND  active  = false;