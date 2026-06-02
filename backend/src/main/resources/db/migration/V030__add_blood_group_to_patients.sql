-- V030__add_blood_group_to_patients.sql
ALTER TABLE patients ADD COLUMN blood_group VARCHAR(5);
