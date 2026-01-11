-- -------------------------------------------------------------------
-- Provisioner role
-- Purpose:
--  - Create roles (pwd / priv / owner / crt)
--  - Create databases
--  - Assign ownership
--  - Terminate sessions during DROP DATABASE
--  - NO superuser privileges
-- -------------------------------------------------------------------

BEGIN;

-- 1) Create provisioner role
CREATE ROLE provisioner
    LOGIN
    CREATEDB
    CREATEROLE
    NOREPLICATION
    NOBYPASSRLS
    NOSUPERUSER
    CONNECTION LIMIT -1;

-- 2) Allow terminating backend connections (for DROP DATABASE safety)
GRANT pg_signal_backend TO provisioner;

-- 3) Optional: explicitly deny dangerous defaults (defense-in-depth)
REVOKE ALL ON DATABASE postgres FROM provisioner;
REVOKE ALL ON SCHEMA public FROM provisioner;

-- 4) Optional: ensure provisioner cannot accidentally own tenant DBs
-- (Ownership is transferred via SET ROLE owner_*)
ALTER ROLE provisioner SET role = NONE;

COMMIT;