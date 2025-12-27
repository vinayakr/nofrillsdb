CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email text NOT NULL UNIQUE,
    name text NOT NULL,
    databases jsonb
);

CREATE INDEX idx_users_email ON users(email);