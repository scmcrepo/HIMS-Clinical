-- Data recovery for affected diagnostic_order_lines records where result_value was set to 'Report saved'

-- 1. For single-parameter tests, restore the actual test value from diagnostic_reports
WITH single_reports AS (
    SELECT diagnostic_order_line_id, MIN(value) as val, COUNT(*) as cnt
    FROM diagnostic_reports
    GROUP BY diagnostic_order_line_id
)
UPDATE diagnostic_order_lines dol
SET result_value = sr.val
FROM single_reports sr
WHERE dol.id = sr.diagnostic_order_line_id
  AND dol.result_value = 'Report saved'
  AND sr.cnt = 1;

-- 2. Clear 'Report saved' to NULL for any remaining/multi-parameter tests
UPDATE diagnostic_order_lines
SET result_value = NULL
WHERE result_value = 'Report saved';
