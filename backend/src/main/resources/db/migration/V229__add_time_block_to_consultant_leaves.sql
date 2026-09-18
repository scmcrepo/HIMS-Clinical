-- ── Partial-day (time-range) blocking for consultant availability ───────────
--  consultant_leaves could only express "this doctor is away for these whole
--  days". A doctor who is in theatre from 09:00 to 12:00 but seeing patients
--  in the afternoon had no way to say so: blocking the day cancelled the
--  afternoon too, and not blocking it let reception book into theatre hours.
--
--  A TIME_RANGE row carries start_time/end_time and blocks only those hours on
--  every date from start_date to end_date. Non-contiguous ranges on one day
--  (09:00-12:00 and 15:00-16:00) are two rows, not one row with a list — it
--  keeps the availability query a plain range overlap instead of a JSONB scan,
--  and unblocking one range without the other is then an ordinary soft delete.
--
--  Rollback: DROP the three columns and the index. Existing rows are unaffected
--  because FULL_DAY reproduces exactly the pre-migration behaviour.

ALTER TABLE consultant_leaves
    ADD COLUMN IF NOT EXISTS block_type VARCHAR(20) NOT NULL DEFAULT 'FULL_DAY',
    ADD COLUMN IF NOT EXISTS start_time TIME,
    ADD COLUMN IF NOT EXISTS end_time   TIME;

COMMENT ON COLUMN consultant_leaves.block_type IS
    'FULL_DAY | TIME_RANGE — TIME_RANGE blocks only start_time..end_time on each date in the range';
COMMENT ON COLUMN consultant_leaves.start_time IS
    'Inclusive start of the blocked window; NULL for FULL_DAY';
COMMENT ON COLUMN consultant_leaves.end_time IS
    'Exclusive end of the blocked window; NULL for FULL_DAY';

-- Every existing row predates partial blocking and is therefore a full-day
-- leave. The column default already wrote FULL_DAY; this is belt-and-braces
-- for rows a concurrent deploy may have inserted with an explicit NULL.
UPDATE consultant_leaves SET block_type = 'FULL_DAY' WHERE block_type IS NULL;

-- A TIME_RANGE row without both times is meaningless and would silently block
-- nothing. Enforced here as well as in the application layer because the
-- availability query trusts these columns.
ALTER TABLE consultant_leaves
    DROP CONSTRAINT IF EXISTS ck_cl_time_range_complete;
ALTER TABLE consultant_leaves
    ADD CONSTRAINT ck_cl_time_range_complete CHECK (
        block_type <> 'TIME_RANGE'
        OR (start_time IS NOT NULL AND end_time IS NOT NULL AND start_time < end_time)
    );

-- The availability path asks "does this consultant have a TIME_RANGE block
-- covering this date?" on every slot lookup, so the partial index keeps that
-- off the full-day rows, which are the overwhelming majority.
CREATE INDEX IF NOT EXISTS idx_cl_time_block
    ON consultant_leaves (consultant_id, start_date, end_date)
    WHERE block_type = 'TIME_RANGE';
