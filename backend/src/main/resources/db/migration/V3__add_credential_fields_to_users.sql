ALTER TABLE users
ADD COLUMN serial text,
ADD COLUMN fingerprint text,
ADD COLUMN issued_at TIMESTAMP,
ADD COLUMN expires_at TIMESTAMP;