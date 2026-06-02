-- V018: Allow diagnostic orders to be placed without a clinical encounter
ALTER TABLE diagnostic_orders ALTER COLUMN encounter_id DROP NOT NULL;
