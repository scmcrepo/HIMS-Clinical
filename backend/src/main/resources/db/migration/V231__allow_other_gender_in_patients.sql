-- Allow OTHER gender (ordinal 2) in patients table
ALTER TABLE patients DROP CONSTRAINT IF EXISTS patients_gender_check;
ALTER TABLE patients ADD CONSTRAINT patients_gender_check CHECK (gender IN (0, 1, 2));

COMMENT ON COLUMN patients.gender IS '0=MALE, 1=FEMALE, 2=OTHER';
