BEGIN;

-- 1) Create the auth function
CREATE OR REPLACE FUNCTION public.pgbouncer_get_auth(username text)
    RETURNS TABLE(usename name, passwd text)
    LANGUAGE sql
    SECURITY DEFINER
    SET search_path = pg_catalog
AS $$
SELECT rolname::name, rolpassword
FROM pg_authid
WHERE rolname = username;
$$;

-- 2) Ensure ownership is postgres (important for SECURITY DEFINER)
    ALTER FUNCTION public.pgbouncer_get_auth(text) OWNER TO postgres;

-- 3) Lock down privileges (defense in depth)
REVOKE ALL ON FUNCTION public.pgbouncer_get_auth(text) FROM PUBLIC;

-- 4) Allow PgBouncer to execute it
GRANT EXECUTE ON FUNCTION public.pgbouncer_get_auth(text) TO pgbouncer_auth;

COMMIT;