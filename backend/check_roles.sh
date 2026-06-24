#!/bin/bash
sudo -u postgres psql -d hims_db -c "SELECT id, name, branch_id FROM roles WHERE name IN ('SUPERADMIN', 'HOSPITAL_ADMIN', 'ADMIN');"
