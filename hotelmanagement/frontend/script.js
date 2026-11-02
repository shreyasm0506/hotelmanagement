document.addEventListener("DOMContentLoaded", function() {

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

    const savedTheme = localStorage.getItem("hotel-theme");

    if (savedTheme === "light") {
        document.body.classList.remove("dark-mode");
    } else {
        document.body.classList.add("dark-mode");
    }

    updateThemeButton();

    if (themeToggle) {
        themeToggle.addEventListener("click", function() {
            document.body.classList.toggle("dark-mode");

            const isDark =
                document.body.classList.contains("dark-mode");

            localStorage.setItem(
                "hotel-theme",
                isDark ? "dark" : "light"
            );

            updateThemeButton();
        });
    }


    // ---------- Current Year in Footer ----------

    const currentYear = document.getElementById("current-year");

    if (currentYear) {
        currentYear.textContent = new Date().getFullYear();
    }


    // ---------- Elements ----------

    const ROOM_TYPES_URL = Auth.API + "/api/rooms/types";
    const roomContainer = document.getElementById("room-container");
    const searchCheckIn = document.getElementById("room-check-in");
    const searchCheckOut = document.getElementById("room-check-out");
    const searchButton = document.getElementById("search-rooms");


    // ---------- Date Search Setup ----------

    const now = new Date();
    const localToday =
        now.getFullYear() + "-" +
        String(now.getMonth() + 1).padStart(2, "0") + "-" +
        String(now.getDate()).padStart(2, "0");

    function showMessage(text) {
        roomContainer.innerHTML = "";

        const p = document.createElement("p");
        p.className = "loading-message";
        p.textContent = text;
        roomContainer.appendChild(p);
    }

    if (searchCheckIn && searchCheckOut) {
        searchCheckIn.min = localToday;
        searchCheckOut.min = localToday;

        searchCheckIn.addEventListener("change", function() {
            searchCheckOut.min = searchCheckIn.value || localToday;

            if (
                searchCheckOut.value &&
                searchCheckOut.value <= searchCheckIn.value
            ) {
                searchCheckOut.value = "";
            }
        });
    }

    if (searchButton) {
        searchButton.addEventListener("click", function() {
            const checkIn = searchCheckIn.value;
            const checkOut = searchCheckOut.value;

            // No dates chosen: show every room type with rooms available
            if (!checkIn && !checkOut) {
                loadRoomTypes();
                return;
            }

            if (!checkIn || !checkOut) {
                showMessage("Please select both check-in and check-out dates.");
                return;
            }

            if (checkIn < localToday) {
                showMessage("Check-in date cannot be in the past.");
                return;
            }

            if (checkOut <= checkIn) {
                showMessage("Check-out must be after check-in.");
                return;
            }

            loadRoomTypes(checkIn, checkOut);
        });
    }


    // ---------- Load Room Types from Spring Boot ----------

    async function loadRoomTypes(checkIn = "", checkOut = "") {
        if (!roomContainer) {
            return;
        }

        showMessage("Loading available rooms...");

        try {
            let url = ROOM_TYPES_URL;

            if (checkIn && checkOut) {
                url += "?" + new URLSearchParams({
                    checkInDate: checkIn,
                    checkOutDate: checkOut
                }).toString();
            }

            const response = await fetch(url);

            if (!response.ok) {
                throw new Error(
                    "Server returned status " + response.status
                );
            }

            const result = await response.json();

            const types = (Array.isArray(result) ? result : []).filter(
                function(type) {
                    return type.availableRooms > 0;
                }
            );

            if (types.length === 0) {
                showMessage(
                    checkIn && checkOut ?
                    "No rooms are available for those dates. Try different dates." :
                    "No rooms found. Please check back later."
                );
                return;
            }

            roomContainer.innerHTML = "";

            types.forEach(function(type) {
                const card = document.createElement("article");
                card.className = "room-card";

                const image = document.createElement("img");
                image.src =
                    "https://images.unsplash.com/photo-1611892440504-42a792e24d32?auto=format&fit=crop&w=900&q=80";
                image.alt = type.roomType + " hotel room";
                image.loading = "lazy";

                const content = document.createElement("div");
                content.className = "room-card-content";

                const title = document.createElement("h3");
                title.textContent = type.roomType;

                const availability = document.createElement("p");
                availability.textContent =
                    type.availableRooms + " of " + type.totalRooms +
                    (type.totalRooms === 1 ? " room" : " rooms") +
                    " available";

                const price = document.createElement("p");
                price.className = "room-price";

                const priceValue = Number(type.startingPrice);

                price.textContent = Number.isFinite(priceValue) && priceValue > 0 ?
                    "From ₹" + priceValue.toLocaleString("en-IN", {
                        minimumFractionDigits: 2,
                        maximumFractionDigits: 2
                    }) + " / night" :
                    "Price not set";

                // Link the card to the booking page with the type (and dates)
                const bookParams = new URLSearchParams({
                    roomType: type.roomType
                });

                if (checkIn && checkOut) {
                    bookParams.set("checkIn", checkIn);
                    bookParams.set("checkOut", checkOut);
                }

                const bookLink = document.createElement("a");
                bookLink.className = "btn";
                bookLink.href = "booking.html?" + bookParams.toString();
                bookLink.textContent = "Book This Room";

                content.appendChild(title);
                content.appendChild(availability);
                content.appendChild(price);
                content.appendChild(bookLink);

                card.appendChild(image);
                card.appendChild(content);

                roomContainer.appendChild(card);
            });

        } catch (error) {
            console.error("Unable to load rooms:", error);

            showMessage(
                "Unable to load rooms. Make sure the Spring Boot backend is running and the API is accessible. Please refresh the page to try again."
            );
        }
    }

    loadRoomTypes();

});
