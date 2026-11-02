// Shared login helpers. Load this before the page's own script.
(function () {
    const API = String(
        (window.HOTEL_CONFIG && window.HOTEL_CONFIG.API_BASE) || "http://localhost:8080"
    ).replace(/\/+$/, "");
    const TOKEN_KEY = "hotel-token";
    const USER_KEY = "hotel-user";

    function read(key) {
        try {
            return localStorage.getItem(key);
        } catch (error) {
            return null;
        }
    }

    function write(key, value) {
        try {
            localStorage.setItem(key, value);
        } catch (error) {
            console.error("Unable to save login:", error);
        }
    }

    function remove(key) {
        try {
            localStorage.removeItem(key);
        } catch (error) {
            /* ignore */
        }
    }

    function getToken() {
        return read(TOKEN_KEY);
    }

    function getUser() {
        try {
            return JSON.parse(read(USER_KEY) || "null");
        } catch (error) {
            return null;
        }
    }

    function isLoggedIn() {
        return Boolean(getToken());
    }

    function saveSession(token, user) {
        write(TOKEN_KEY, token);
        write(USER_KEY, JSON.stringify(user));
    }

    function clearSession() {
        remove(TOKEN_KEY);
        remove(USER_KEY);
    }

    // Only allow redirects to pages in this site, never to another website
    function safeNext(next) {
        if (typeof next === "string" &&
            /^[A-Za-z0-9_-]+\.html(\?[A-Za-z0-9_=&%.+-]*)?$/.test(next)) {
            return next;
        }

        return "index.html";
    }

    function redirectToLogin() {
        const page = window.location.pathname.split("/").pop() || "index.html";
        const next = page + window.location.search;

        window.location.href =
            "login.html?next=" + encodeURIComponent(safeNext(next));
    }

    // Call at the top of a page that needs a logged-in guest.
    // Returns false (and starts a redirect) when nobody is logged in.
    function requireLogin() {
        if (isLoggedIn()) {
            return true;
        }

        redirectToLogin();
        return false;
    }

    function logout() {
        clearSession();
        window.location.href = "index.html";
    }

    // fetch() to the API with the login token attached. If the server says the
    // token is no longer valid (expired), the guest is sent to the login page.
    async function apiFetch(path, options) {
        const settings = Object.assign({}, options);
        const headers = Object.assign({}, settings.headers);
        const token = getToken();

        if (token) {
            headers.Authorization = "Bearer " + token;
        }

        settings.headers = headers;

        const response = await fetch(API + path, settings);

        if (response.status === 401 && token) {
            clearSession();
            redirectToLogin();
        }

        return response;
    }

    // Fills <div id="auth-area"> in the header
    function renderAuthArea() {
        const area = document.getElementById("auth-area");

        if (!area) {
            return;
        }

        area.replaceChildren();

        const user = getUser();

        if (isLoggedIn() && user) {
            const firstName = (user.fullName || "Guest").split(" ")[0];

            const name = document.createElement("a");
            name.className = "auth-name";
            name.href = "my-bookings.html";
            name.textContent = "Hi, " + firstName;
            name.title = "My bookings";

            const button = document.createElement("button");
            button.type = "button";
            button.className = "btn btn-secondary auth-button";
            button.textContent = "Log out";
            button.addEventListener("click", logout);

            area.appendChild(name);
            area.appendChild(button);
        } else {
            const link = document.createElement("a");
            link.className = "btn btn-secondary auth-button";
            link.href = "login.html";
            link.textContent = "Log in";

            area.appendChild(link);
        }
    }

    window.Auth = {
        API: API,
        getToken: getToken,
        getUser: getUser,
        isLoggedIn: isLoggedIn,
        saveSession: saveSession,
        clearSession: clearSession,
        safeNext: safeNext,
        requireLogin: requireLogin,
        redirectToLogin: redirectToLogin,
        logout: logout,
        apiFetch: apiFetch,
        renderAuthArea: renderAuthArea
    };

    renderAuthArea();
})();
