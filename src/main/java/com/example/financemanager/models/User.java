// src/main/java/com/example/financemanager/model/User.java
package com.example.financemanager.models;

import java.util.UUID;

public class User {
    private UUID id;
    private String email;
    private String passwordHash;

    public User() {}

    public User(UUID id, String email, String passwordHash) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }

    public void setId(UUID id) { this.id = id; }
    public void setEmail(String email) { this.email = email; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
}
