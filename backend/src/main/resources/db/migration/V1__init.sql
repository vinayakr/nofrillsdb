CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email text NOT NULL UNIQUE,
    name text NOT NULL,
    role text,
    crt_role text,
    serial text,
    fingerprint text,
    issued_at text,
    expires_at text,
    databases jsonb
);

CREATE INDEX idx_users_email ON users(email);