# Deploying Grand Stay

Three parts: a database (MySQL or PostgreSQL), the Spring Boot backend (this
repo, built with the Dockerfile), and the static frontend (the `frontend` folder).

The project includes both database drivers. The one used is chosen by the start
of `DB_URL`, so local development can stay on MySQL while the deployed site
uses PostgreSQL.

## Backend environment variables

| Variable | Required | Notes |
|---|---|---|
| `DB_URL` | yes (when deployed) | see "Database address" below |
| `DB_USERNAME` | yes | database user |
| `DB_PASSWORD` | yes | database password |
| `AUTH_SECRET` | yes | random string, 32+ characters. Without it everyone is logged out on every restart |
| `CORS_ORIGINS` | yes | your frontend URL, e.g. `https://hotel-site.vercel.app` (comma-separate several, no trailing slash) |
| `PORT` | set by the host | the backend listens on it automatically |
| `SHOW_SQL` | no | `true` to log SQL while debugging |

## Database address

### PostgreSQL (Neon, Supabase, Render, any host)

Hosts show a connection string like

    postgresql://USER:PASSWORD@HOST:PORT/DBNAME

Split it into three variables:

    DB_URL      = jdbc:postgresql://HOST:PORT/DBNAME?sslmode=require
    DB_USERNAME = USER
    DB_PASSWORD = PASSWORD

(If the string has no `:PORT`, leave it out of DB_URL; the default is 5432.
If the string already ends with `?sslmode=require`, don't add it twice.)
If the host offers both a direct and a pooled address and the direct one times
out, try the pooled one.

### MySQL

    DB_URL = jdbc:mysql://HOST:PORT/DBNAME?sslMode=REQUIRED

## Steps

1. Create the database and note its connection details.
2. Test locally first: put `DB_URL`, `DB_USERNAME` and `DB_PASSWORD` in your
   run configuration's environment, start the backend, and open
   `http://localhost:8080/api/rooms`. Seeing `[]` means it connected and the
   tables were created.
3. Create a Web Service from this GitHub repo using the **Docker** runtime and
   add the variables above. Leave `CORS_ORIGINS` for step 5 if you don't have
   the frontend URL yet.
4. When the backend is live, open `frontend/config.js` and set `API_BASE` to the
   backend's public `https://` URL. Commit and push.
5. Deploy the `frontend` folder as a static site (publish directory `frontend`,
   no build command), then set `CORS_ORIGINS` on the backend to the site's URL.
6. Register an account on the live site, then run `sql/setup-admin-postgres.sql`
   (or `sql/setup-admin.sql` for MySQL) against the hosted database to make it
   an admin.
7. Test: browse rooms, register, book, view and cancel from My bookings.

## Local development

Set `DB_PASSWORD` in your run configuration, start the backend, and open the
frontend with Live Server on port 5500. Without `DB_URL`, the backend uses
MySQL on localhost. `config.js` already points at `http://localhost:8080`.
