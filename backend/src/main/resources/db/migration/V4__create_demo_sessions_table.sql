CREATE TABLE demo_sessions (
    token TEXT PRIMARY KEY,
    role TEXT NOT NULL,
    database_name TEXT NOT NULL,
    password TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    storage_exceeded BOOLEAN NOT NULL DEFAULT FALSE,
    cleaned_up BOOLEAN NOT NULL DEFAULT FALSE
);
