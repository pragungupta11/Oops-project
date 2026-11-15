package com.iitsm.iitsm.model;

public enum Category {
    NETWORK("Network"), DATABASE("Database"), APPLICATION("Application"),
    SECURITY("Security"), AUTHENTICATION("Authentication"), CLOUD("Cloud");

    private final String label;
    Category(String label) { this.label = label; }
    public String label() { return label; }
}