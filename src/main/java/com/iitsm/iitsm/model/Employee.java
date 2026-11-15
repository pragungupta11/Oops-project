package com.iitsm.iitsm.model;

public class Employee extends User {
    private final String department;

    public Employee(String id, String name, String email, String department) {
        super(id, name, email);
        this.department = department;
    }

    public String getDepartment() { return department; }
    @Override public Role getRole() { return Role.EMPLOYEE; }
    @Override public boolean canViewAllIncidents() { return false; }
}