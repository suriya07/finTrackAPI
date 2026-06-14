package com.example.financemanager.entities;

import jakarta.persistence.*;
import java.util.UUID;

/**
 * A user-defined grouping of expenses (e.g. a trip or event) used to total
 * spending across categories and accounts. Expenses reference a group via an
 * optional {@code group_id}. Table is named {@code expense_groups} to avoid the
 * reserved word "groups".
 */
@Entity
@Table(name = "expense_groups")
public class GroupEntity extends BaseAuditableEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private UserEntity user;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    public GroupEntity() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UserEntity getUser() {
        return user;
    }

    public void setUser(UserEntity user) {
        this.user = user;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
