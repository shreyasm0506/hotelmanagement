document.addEventListener("DOMContentLoaded", function() {
    if (!Auth.requireLogin()) {
        return;
    }

    const list = document.getElementById("bookings-list");
    const message = document.getElementById("bookings-message");

    function parseDate(value) {
        const parts = String(value).split("-").map(Number);
        return new Date(parts[0], parts[1] - 1, parts[2]);
    }

    function formatDate(value) {
        return parseDate(value).toLocaleDateString("en-IN", {
            day: "numeric",
            month: "short",
            year: "numeric"
        });
    }

    function nightsBetween(checkIn, checkOut) {
        return Math.round((parseDate(checkOut) - parseDate(checkIn)) / 86400000);
    }

    function todayString() {
        const now = new Date();

        return now.getFullYear() + "-" +
            String(now.getMonth() + 1).padStart(2, "0") + "-" +
            String(now.getDate()).padStart(2, "0");
    }

    function addLine(parent, label, value) {
        const row = document.createElement("div");
        row.className = "booking-line";

        const labelEl = document.createElement("span");
        labelEl.textContent = label;

        const valueEl = document.createElement("strong");
        valueEl.textContent = value;

        row.appendChild(labelEl);
        row.appendChild(valueEl);
        parent.appendChild(row);
    }

    function renderBooking(booking, room) {
        const item = document.createElement("article");
        item.className = "booking-item";

        const head = document.createElement("div");
        head.className = "booking-item-head";

        const title = document.createElement("h2");
        title.textContent = room ?
            (room.roomType || "Hotel Room") + " - Room " + room.roomNumber :
            "Room " + booking.roomId;

        const status = String(booking.status || "").toUpperCase();

        const badge = document.createElement("span");
        badge.className = "status-badge status-" + status.toLowerCase();
        badge.textContent = status.charAt(0) + status.slice(1).toLowerCase();

        head.appendChild(title);
        head.appendChild(badge);
        item.appendChild(head);

        const nights = nightsBetween(booking.checkInDate, booking.checkOutDate);

        addLine(item, "Check-in", formatDate(booking.checkInDate));
        addLine(item, "Check-out", formatDate(booking.checkOutDate));
        addLine(item, "Length of stay", nights + (nights === 1 ? " night" : " nights"));

        if (booking.guestCount != null) {
            addLine(item, "Guests", booking.guestCount);
        }

        const actions = document.createElement("div");
        actions.className = "booking-item-actions";

        const view = document.createElement("a");
        view.className = "btn btn-secondary";
        view.href = "confirmation.html?id=" + encodeURIComponent(booking.id);
        view.textContent = "View confirmation";
        actions.appendChild(view);

        const canCancel = status === "CONFIRMED" &&
            booking.checkOutDate >= todayString();

        if (canCancel) {
            const cancel = document.createElement("button");
            cancel.type = "button";
            cancel.className = "btn btn-secondary btn-danger";
            cancel.textContent = "Cancel booking";

            cancel.addEventListener("click", async function() {
                if (!window.confirm("Cancel this booking?")) {
                    return;
                }

                cancel.disabled = true;

                try {
                    const response = await Auth.apiFetch(
                        "/api/bookings/" + booking.id + "/cancel",
                        { method: "PUT" }
                    );

                    if (!response.ok) {
                        throw new Error(
                            (await response.text()) || "Could not cancel the booking."
                        );
                    }

                    loadBookings();
                } catch (error) {
                    console.error("Cancel error:", error);
                    message.textContent = error.message;
                    cancel.disabled = false;
                }
            });

            actions.appendChild(cancel);
        }

        item.appendChild(actions);
        return item;
    }

    async function loadBookings() {
        list.replaceChildren();
        message.textContent = "Loading your bookings...";

        try {
            const results = await Promise.all([
                Auth.apiFetch("/api/bookings/mine"),
                fetch(Auth.API + "/api/rooms")
            ]);

            const bookingsResponse = results[0];
            const roomsResponse = results[1];

            if (!bookingsResponse.ok) {
                throw new Error("Unable to load your bookings.");
            }

            const bookings = await bookingsResponse.json();
            const rooms = roomsResponse.ok ? await roomsResponse.json() : [];

            const roomsById = new Map(
                rooms.map(function(room) {
                    return [room.id, room];
                })
            );

            if (bookings.length === 0) {
                message.replaceChildren(
                    document.createTextNode("You have no bookings yet. "),
                    Object.assign(document.createElement("a"), {
                        href: "index.html#rooms",
                        textContent: "Browse rooms"
                    })
                );
                return;
            }

            message.textContent = "";

            bookings.forEach(function(booking) {
                list.appendChild(
                    renderBooking(booking, roomsById.get(booking.roomId))
                );
            });
        } catch (error) {
            console.error("Unable to load bookings:", error);
            message.textContent =
                "Unable to load your bookings. Make sure the backend is running.";
        }
    }

    loadBookings();
});
