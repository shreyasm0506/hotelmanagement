# Deploying Grand Stay

Three parts: a MySQL database, the Spring Boot backend (this repo, built with
the Dockerfile), and the static frontend (the `frontend` folder).

## Backend environment variables

| Variable | Required | Example / notes |
|---|---|---|
| `DB_URL` | yes (when deployed) | `jdbc:mysql://HOST:PORT/DBNAME` (some hosts need `?useSSL=true&serverTimezone=UTC` added) |
| `DB_USERNAME` | yes | database user |
| `DB_PASSWORD` | yes | database password |
| `AUTH_SECRET` | yes | random string, 32+ characters. Without it everyone is logged out on every restart |
| `CORS_ORIGINS` | yes | your frontend URL, e.g. `https://hotel-site.netlify.app` (comma-separate several, no trailing slash) |
| `PORT` | set by the host | the backend listens on it automatically |
| `SHOW_SQL` | no | `true` to log SQL while debugging |

## Steps

1. Create a MySQL database and note host, port, name, user and password.
2. Create a Web Service from this GitHub repo using the **Docker** runtime
   (the Dockerfile is in the project root) and add the variables above.
   Leave `CORS_ORIGINS` for step 4 if you don't have the frontend URL yet.
3. When the backend is live, open `frontend/config.js` and set `API_BASE` to the
   backend's public `https://` URL. Commit and push.
4. Create a static site with **publish directory `frontend`** (no build command).
   Then set `CORS_ORIGINS` on the backend to the site's URL and redeploy it.
5. Register an account on the live site, then run `sql/setup-admin.sql`
   against the hosted database to make it an admin.
6. Test: browse rooms, register, book, view and cancel from My bookings.

## Local development (unchanged)

Set `DB_PASSWORD` in your run configuration, start the backend, and open the
frontend with Live Server on port 5500. `config.js` already points at
`http://localhost:8080`.
