-- V2__seed_reference_data.sql
--
-- Reference data — roles and the initial category list — belongs in a
-- migration (versioned, code-reviewed, runs exactly once), not in
-- application startup code. Demo USER ACCOUNTS are intentionally NOT here;
-- see DataSeeder.java for why those are environment-conditional Java code
-- instead.

INSERT INTO roles (name, description) VALUES
    ('USER',  'Student or staff member who submits complaints'),
    ('STAFF', 'Reviews and resolves complaints assigned to them'),
    ('ADMIN', 'Full administrative access: user management, reporting, category management');

INSERT INTO categories (name, description, active, created_at) VALUES
    ('IT & Infrastructure',   'Network, computers, projectors, campus IT systems', TRUE, NOW()),
    ('Academics',             'Curriculum, faculty, examinations, grading',         TRUE, NOW()),
    ('Hostel & Facilities',   'Hostel rooms, water, electricity, maintenance',      TRUE, NOW()),
    ('Library',               'Books, study spaces, library systems',               TRUE, NOW()),
    ('Transport',             'College buses, parking, shuttle services',           TRUE, NOW()),
    ('Administration',        'Fees, certificates, administrative processes',       TRUE, NOW()),
    ('Canteen',                'Food quality, hygiene, canteen services',            TRUE, NOW()),
    ('Other',                  'Anything that does not fit the categories above',    TRUE, NOW());
