CREATE TABLE users (
    id            UUID         PRIMARY KEY,
    email         VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    name          VARCHAR(100) NOT NULL,
    role          VARCHAR(32)  NOT NULL,
    rating        NUMERIC(3, 2),
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT chk_users_role CHECK (role IN ('USER', 'VOLUNTEER', 'ORGANIZER', 'ADMIN')),
    CONSTRAINT chk_users_rating CHECK (rating BETWEEN 1 AND 5)
);

CREATE UNIQUE INDEX uq_users_email_lower ON users (lower(email));
