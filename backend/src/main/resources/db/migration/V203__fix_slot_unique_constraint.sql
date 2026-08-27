-- Fix unique constraint on appointment_slots to allow same time on different specific dates
-- The old constraint (consultant_id, day_of_week, concat_time) prevented date-specific slots
-- that share the same day/time as recurring slots. Adding specific_date to the constraint
-- allows both: one recurring row (specific_date=NULL) and one date-specific row per date.
ALTER TABLE appointment_slots DROP CONSTRAINT IF EXISTS uq_as_provider_day_time;
ALTER TABLE appointment_slots ADD CONSTRAINT uq_as_provider_day_time 
    UNIQUE (consultant_id, day_of_week, concat_time, specific_date);
