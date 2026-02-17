-- Test persona so PersonaService doesn't trigger interactive setup
INSERT INTO personas (id, name, pronouns, description, other_details, created_at, updated_at)
VALUES (1, 'Test User', 'they/them', 'A test persona', 'For integration tests', NOW(), NOW());