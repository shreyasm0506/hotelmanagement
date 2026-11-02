document.addEventListener("DOMContentLoaded", function() {

    // Booking details are private to the guest who made the booking
    if (!Auth.requireLogin()) {
        return;
    }

    const detailsContainer = document.getElementById("confirmation-details");
    const message = document.getElementById("confirmation-message");
    const themeToggle = document.getElementById("theme-toggle");
    const themeIcon = document.getElementById("theme-icon");
    const printButton = document.getElementById("print-booking");

    const savedTheme = localStorage.getItem("hotel-theme");

    if (savedTheme === "light") {
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

    if (printButton) {
        printButton.addEventListener("click", function() {
            window.print();
        });
    }

    const params = new URLSearchParams(window.location.search);
    const bookingId = params.get("id");

    if (!bookingId || !/^\d+$/.test(bookingId)) {
        detailsContainer.textContent = "No valid booking ID was provided.";
        return;
    }

    async function loadBooking() {
        try {
            const response = await Auth.apiFetch(
                "/api/bookings/" + bookingId
            );

            if (!response.ok) {
                throw new Error("Booking not found.");
            }

            const booking = await response.json();

            let room = null;

            try {
                const roomResponse = await fetch(
                    Auth.API + "/api/rooms/" + booking.roomId
                );

                if (roomResponse.ok) {
                    room = await roomResponse.json();
                }
            } catch (roomError) {
                console.error("Unable to load room details:", roomError);
            }

            detailsContainer.replaceChildren();

            const rows = [
                ["Booking ID", booking.id],
                ["Room", room ?
                    (room.roomType || "Hotel Room") +
                    " - Room " + (room.roomNumber || booking.roomId) :
                    "Room ID " + booking.roomId
                ],
                ["Check-in", booking.checkInDate],
                ["Check-out", booking.checkOutDate],
                ["Guests", booking.guestCount != null ? booking.guestCount : "Not specified"],
                ["Status", booking.status]
            ];

            rows.forEach(function(item) {
                const row = document.createElement("div");
                row.className = "confirmation-row";

                const label = document.createElement("span");
                label.textContent = item[0];

                const value = document.createElement("strong");
                value.textContent = item[1];

                row.appendChild(label);
                row.appendChild(value);
                detailsContainer.appendChild(row);
            });

            message.textContent = "";
        } catch (error) {
            console.error("Unable to load booking:", error);
            detailsContainer.textContent =
                "Unable to load booking details. Make sure the backend is running and the booking ID is correct.";
        }
    }

    loadBooking();
});
