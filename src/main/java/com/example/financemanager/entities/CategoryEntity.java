package com.example.financemanager.entities;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "categories")
public class CategoryEntity extends BaseAuditableEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private UserEntity user;

    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private CategoryEntity parent;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, orphanRemoval = true)
    private java.util.List<CategoryEntity> subCategories = new java.util.ArrayList<>();

    // Flag/Limit for nesting: How many levels deep can children go from this
    // category?
    // 0 means no children allowed. Default could be null (unlimited) or specific
    // value.
    private Integer allowedNestingDepth;

    public CategoryEntity() {
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

    public CategoryEntity getParent() {
        return parent;
    }

    public void setParent(CategoryEntity parent) {
        this.parent = parent;
    }

    public java.util.List<CategoryEntity> getSubCategories() {
        return subCategories;
    }

    public void setSubCategories(java.util.List<CategoryEntity> subCategories) {
        this.subCategories = subCategories;
    }

    public Integer getAllowedNestingDepth() {
        return allowedNestingDepth;
    }

    public void setAllowedNestingDepth(Integer allowedNestingDepth) {
        this.allowedNestingDepth = allowedNestingDepth;
    }
}
