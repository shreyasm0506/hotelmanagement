document.addEventListener("DOMContentLoaded", function() {
    const params = new URLSearchParams(window.location.search);
    const next = Auth.safeNext(params.get("next"));

    // Already logged in: go straight on
    if (Auth.isLoggedIn()) {
        window.location.replace(next);
        return;
    }

    const form = document.getElementById("auth-form");
    const emailInput = document.getElementById("email");
    const passwordInput = document.getElementById("password");
    const submitButton = document.getElementById("auth-submit");
    const message = document.getElementById("auth-message");
    const registerLink = document.getElementById("register-link");

    // Keep the page the guest was heading to when they sign up instead
    if (params.get("next")) {
        registerLink.href = "register.html?next=" + encodeURIComponent(next);
    }

    form.addEventListener("submit", async function(event) {
        event.preventDefault();

        const email = emailInput.value.trim();
        const password = passwordInput.value;

        if (!email || !password) {
            message.textContent = "Enter your email and password.";
            return;
        }

        submitButton.disabled = true;
        message.textContent = "Logging in...";

        try {
            const response = await fetch(Auth.API + "/api/auth/login", {
                method: "POST",
                headers: {
                    "Content-Type": "application/json"
                },
                body: JSON.stringify({
                    email: email,
                    password: password
                })
            });

            const text = await response.text();

            if (!response.ok) {
                throw new Error(text || "Login failed.");
            }

            const data = JSON.parse(text);

            Auth.saveSession(data.token, data.guest);
            window.location.href = next;
        } catch (error) {
            console.error("Login error:", error);

            message.textContent = error instanceof TypeError ?
                "Cannot reach the server. Make sure the backend is running." :
                error.message;

            submitButton.disabled = false;
        }
    });
});
