UPDATE courts
SET    active     = true,
       updated_at = NOW()
WHERE  code IN ('STF', 'EPROC', 'STJRJ');