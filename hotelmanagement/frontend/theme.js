// Light/dark toggle for the login, register and My Bookings pages
document.addEventListener("DOMContentLoaded", function() {
    const themeToggle = document.getElementById("theme-toggle");
    const themeIcon = document.getElementById("theme-icon");

    if (localStorage.getItem("hotel-theme") === "light") {
        document.body.classList.remove("dark-mode");
    } else {
        document.body.classList.add("dark-mode");
    }

    function updateThemeButton() {
        const isDark = document.body.classList.contains("dark-mode");

        if (themeIcon) {
            themeIcon.textContent = isDark ? "Light" : "Dark";
        }

        if (themeToggle) {
            themeToggle.setAttribute(
                "aria-label",
                isDark ? "Switch to light mode" : "Switch to dark mode"
            );
        }
    }

    updateThemeButton();

    if (themeToggle) {
        themeToggle.addEventListener("click", function() {
            document.body.classList.toggle("dark-mode");

            const isDark = document.body.classList.contains("dark-mode");
            localStorage.setItem("hotel-theme", isDark ? "dark" : "light");

            updateThemeButton();
        });
    }
});
