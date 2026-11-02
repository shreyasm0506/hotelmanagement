document.addEventListener("DOMContentLoaded", function() {
    const params = new URLSearchParams(window.location.search);
    const next = Auth.safeNext(params.get("next"));

    if (Auth.isLoggedIn()) {
        window.location.replace(next);
        return;
    }

    const form = document.getElementById("auth-form");
    const fullNameInput = document.getElementById("full-name");
    const emailInput = document.getElementById("email");
    const phoneInput = document.getElementById("phone");
    const passwordInput = document.getElementById("password");
    const confirmInput = document.getElementById("confirm-password");
    const submitButton = document.getElementById("auth-submit");
    const message = document.getElementById("auth-message");
    const loginLink = document.getElementById("login-link");

    if (params.get("next")) {
        loginLink.href = "login.html?next=" + encodeURIComponent(next);
    }

    form.addEventListener("submit", async function(event) {
        event.preventDefault();

        const fullName = fullNameInput.value.trim();
        const email = emailInput.value.trim();
        const phone = phoneInput.value.trim();
        const password = passwordInput.value;

        if (!fullName) {
            message.textContent = "Enter your full name.";
            return;
        }

        if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) {
            message.textContent = "Enter a valid email address.";
            return;
        }

        if (password.length < 8) {
            message.textContent = "Password must be at least 8 characters.";
            return;
        }

        if (password !== confirmInput.value) {
            message.textContent = "The two passwords don't match.";
            return;
        }

        submitButton.disabled = true;
        message.textContent = "Creating your account...";

        try {
            const response = await fetch(Auth.API + "/api/auth/register", {
                method: "POST",
                headers: {
                    "Content-Type": "application/json"
                },
                body: JSON.stringify({
                    fullName: fullName,
                    email: email,
                    phoneNumber: phone,
                    password: password
                })
            });

            const text = await response.text();

            if (!response.ok) {
                throw new Error(text || "Could not create the account.");
            }

            const data = JSON.parse(text);

            Auth.saveSession(data.token, data.guest);
            window.location.href = next;
        } catch (error) {
            console.error("Register error:", error);

            message.textContent = error instanceof TypeError ?
                "Cannot reach the server. Make sure the backend is running." :
                error.message;

            submitButton.disabled = false;
        }
    });
});
