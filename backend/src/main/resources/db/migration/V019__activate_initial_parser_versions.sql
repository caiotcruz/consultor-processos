UPDATE parser_versions
SET    active      = true,
       released_at = NOW(),
       released_by = 'system-phase-5'
WHERE  version = '1.0.0';