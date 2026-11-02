package com.hotel.hotelmanagement;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "guests")
public class Guest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fullName;

    private String phoneNumber;

    @Column(unique = true)
    private String email;

    // BCrypt hash. Null for guests created before login existed.
    @Column(name = "password_hash")
    private String passwordHash;

    // "GUEST" or "ADMIN". Null (older rows) is treated as GUEST.
    @Column(name = "user_role", length = 20)
    private String role;

    public Guest() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    // Never serialize the hash. The controllers return GuestResponse, and
    // this keeps the hash out of JSON even if a Guest is returned directly.
    @JsonIgnore
    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getRole() {
        return role == null || role.isBlank() ? "GUEST" : role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
