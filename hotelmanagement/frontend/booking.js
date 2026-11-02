document.addEventListener("DOMContentLoaded", function() {

    // Booking needs a logged-in guest
    if (!Auth.requireLogin()) {
        return;
    }

    // ---------- Theme Toggle ----------

    const themeToggle = document.getElementById("theme-toggle");
    const themeIcon = document.getElementById("theme-icon");

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

    if (localStorage.getItem("hotel-theme") === "light") {
        document.body.classList.remove("dark-mode");
    } else {
        document.body.classList.add("dark-mode");
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


    // ---------- Elements ----------

    const ROOM_TYPES_URL = Auth.API + "/api/rooms/types";

    const roomSelect = document.getElementById("room-id");
    const bookingForm = document.getElementById("booking-form");
    const checkInInput = document.getElementById("check-in");
    const checkOutInput = document.getElementById("check-out");
    const guestsInput = document.getElementById("guests");
    const bookingMessage = document.getElementById("booking-message");

    // Values passed from the home page (room type card / date search)
    const params = new URLSearchParams(window.location.search);
    let preselectedType = params.get("roomType");


    // ---------- Dates ----------

    const today = new Date();
    const localToday =
        today.getFullYear() + "-" +
        String(today.getMonth() + 1).padStart(2, "0") + "-" +
        String(today.getDate()).padStart(2, "0");

    checkInInput.min = localToday;
    checkOutInput.min = localToday;

    const presetCheckIn = params.get("checkIn");
    const presetCheckOut = params.get("checkOut");

    if (
        presetCheckIn && presetCheckOut &&
        /^\d{4}-\d{2}-\d{2}$/.test(presetCheckIn) &&
        /^\d{4}-\d{2}-\d{2}$/.test(presetCheckOut) &&
        presetCheckIn >= localToday &&
        presetCheckOut > presetCheckIn
    ) {
        checkInInput.value = presetCheckIn;
        checkOutInput.min = presetCheckIn;
        checkOutInput.value = presetCheckOut;
    }

    checkInInput.addEventListener("change", function() {
        checkOutInput.min = checkInInput.value || localToday;

        if (
            checkOutInput.value &&
            checkOutInput.value <= checkInInput.value
        ) {
            checkOutInput.value = "";
        }

        loadRoomTypes();
    });

    checkOutInput.addEventListener("change", loadRoomTypes);


    // ---------- Load Room Types ----------

    // With both dates chosen, availability reflects those dates;
    // otherwise it only reflects rooms that are marked available.
    async function loadRoomTypes() {
        const checkIn = checkInInput.value;
        const checkOut = checkOutInput.value;
        const hasDates = checkIn && checkOut && checkOut > checkIn;

        // Remember the current choice so changing dates doesn't reset it
        const previousChoice = roomSelect.value || preselectedType;

        try {
            let url = ROOM_TYPES_URL;

            if (hasDates) {
                url += "?" + new URLSearchParams({
                    checkInDate: checkIn,
                    checkOutDate: checkOut
                }).toString();
            }

            const response = await fetch(url);

            if (!response.ok) {
                throw new Error("Unable to fetch room types");
            }

            const types = await response.json();

            if (!Array.isArray(types) || types.length === 0) {
                roomSelect.innerHTML =
                    '<option value="">No rooms found</option>';
                return;
            }

            roomSelect.innerHTML = '<option value="">Choose a room type</option>';

            types.forEach(function(type) {
                const option = document.createElement("option");
                option.value = type.roomType;

                const priceValue = Number(type.startingPrice);
                const priceText = Number.isFinite(priceValue) && priceValue > 0 ?
                    " - from ₹" + priceValue.toLocaleString("en-IN") + "/night" :
                    "";

                option.textContent =
                    type.roomType + " (" + type.availableRooms + " of " +
                    type.totalRooms + " available)" + priceText;

                option.disabled = type.availableRooms === 0;
                roomSelect.appendChild(option);
            });

            if (previousChoice) {
                const match = Array.from(roomSelect.options).find(
                    function(option) {
                        return option.value === previousChoice &&
                            !option.disabled;
                    }
                );

                if (match) {
                    roomSelect.value = previousChoice;
                }
            }

            preselectedType = null;
        } catch (error) {
            console.error("Error loading room types:", error);
            roomSelect.innerHTML =
                '<option value="">Could not load rooms</option>';
        }
    }

    loadRoomTypes();


    // ---------- Submit Booking ----------

    bookingForm.addEventListener("submit", async function(event) {
        event.preventDefault();

        const checkIn = checkInInput.value;
        const checkOut = checkOutInput.value;
        const guests = Number(guestsInput.value);
        const roomType = roomSelect.value;

        if (!roomType) {
            bookingMessage.textContent = "Please select a room type.";
            return;
        }

        if (!checkIn || !checkOut || checkIn < localToday) {
            bookingMessage.textContent = "Please select valid dates.";
            return;
        }

        if (checkOut <= checkIn) {
            bookingMessage.textContent =
                "Check-out must be after check-in.";
            return;
        }

        if (!Number.isInteger(guests) || guests < 1 || guests > 10) {
            bookingMessage.textContent =
                "Please enter between 1 and 10 guests.";
            return;
        }

        // The server knows who is booking from the login token
        const bookingData = {
            roomType: roomType,
            checkInDate: checkIn,
            checkOutDate: checkOut,
            guestCount: guests
        };

        bookingMessage.textContent = "Submitting your booking...";

        try {
            const response = await Auth.apiFetch("/api/bookings/by-type", {
                method: "POST",
                headers: {
                    "Content-Type": "application/json"
                },
                body: JSON.stringify(bookingData)
            });

            const responseText = await response.text();

            if (!response.ok) {
                throw new Error(responseText || "Booking failed.");
            }

            const booking = JSON.parse(responseText);

            window.location.href =
                "confirmation.html?id=" + encodeURIComponent(booking.id);
        } catch (error) {
            console.error("Booking error:", error);
            bookingMessage.textContent =
                "Booking failed: " + error.message;
        }
    });

});
