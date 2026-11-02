-- Run in MySQL Workbench or the mysql command line AFTER you have started the
-- backend once (Hibernate adds the new password_hash and user_role columns).

USE hotel_management;

-- 1. Open register.html on the site and create your own account.
-- 2. Make that account an admin (replace the email with yours):
UPDATE guests SET user_role = 'ADMIN' WHERE email = 'you@example.com';

-- 3. Check it worked (the hash column is never shown by the API):
SELECT id, full_name, email, user_role, password_hash IS NOT NULL AS can_log_in FROM guests;

-- Guests created before login existed (such as the old hardcoded guest 3) have
-- no password, so they cannot log in. Leave them, or delete them if they have
-- no bookings:
-- DELETE FROM guests WHERE id = 3;
