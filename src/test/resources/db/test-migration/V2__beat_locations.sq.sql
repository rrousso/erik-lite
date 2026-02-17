-- H2-compatible beat locations
ALTER TABLE beats ADD COLUMN location_name VARCHAR(200);
ALTER TABLE beats ADD COLUMN location_description CLOB;