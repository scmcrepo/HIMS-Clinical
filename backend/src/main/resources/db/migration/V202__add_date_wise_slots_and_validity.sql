-- Add specific_date and effective validity range to appointment_slots
ALTER TABLE appointment_slots
    ADD COLUMN specific_date DATE,
    ADD COLUMN effective_from DATE,
    ADD COLUMN effective_to DATE;

CREATE INDEX idx_as_specific_date ON appointment_slots(consultant_id, specific_date);
CREATE INDEX idx_as_validity ON appointment_slots(consultant_id, effective_from, effective_to);
