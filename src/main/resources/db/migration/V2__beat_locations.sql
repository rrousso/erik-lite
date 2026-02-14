ALTER TABLE beats ADD COLUMN location_name VARCHAR(200);
ALTER TABLE beats ADD COLUMN location_description TEXT;

ALTER TABLE stanza_characters DROP COLUMN IF EXISTS current_location;