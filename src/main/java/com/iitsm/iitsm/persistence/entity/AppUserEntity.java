package com.iitsm.iitsm.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "app_users")
public class AppUserEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String username;

    @Column(nullable = false, length = 100)
    private String password;

    @Column(nullable = false, length = 20)
    private String role;

    @Column(length = 100)
    private String fullName;

    @Column(length = 120, unique = true)
    private String email;

    /** False = deactivated by an admin: login is refused. Null (older rows) counts as active. */
    private Boolean enabled;

    private Instant createdAt;

    protected AppUserEntity() { }

    public AppUserEntity(String username, String password, String role) {
        this(username, password, role, null, null);
    }

    public AppUserEntity(String username, String password, String role, String fullName, String email) {
        this.username = username;
        this.password = password;
        this.role = role;
        this.fullName = fullName;
        this.email = email;
        this.enabled = true;
        this.createdAt = Instant.now();
    }

    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public String getRole() { return role; }
    public String getFullName() { return fullName == null ? username : fullName; }
    public String getEmail() { return email; }
    public boolean isEnabled() { return enabled == null || enabled; }
    public Instant getCreatedAt() { return createdAt; }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
