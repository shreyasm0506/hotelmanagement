-- PostgreSQL version of setup-admin.sql.
-- Run it in the database's SQL console (or psql) AFTER you have started the
-- backend once (Hibernate creates the tables and columns on first start).

-- 1. Open register.html on the site and create your own account.
-- 2. Make that account an admin (replace the email with yours):
UPDATE guests SET user_role = 'ADMIN' WHERE email = 'you@example.com';

-- 3. Check it worked (the hash itself is never shown):
SELECT id, full_name, email, user_role, (password_hash IS NOT NULL) AS can_log_in
FROM guests;
