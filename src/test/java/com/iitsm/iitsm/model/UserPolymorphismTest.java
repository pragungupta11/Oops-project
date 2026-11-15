package com.iitsm.iitsm.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class UserPolymorphismTest {
    @Test void eachSubclassReportsItsOwnRoleThroughTheBaseType() {
        List<User> users = List.of(
            new Employee("E1", "Anu", "a@c.com", "HR"),
            new Technician("T1", "Ravi", Set.of("Network"), 0, true),
            new Administrator("A1", "Meena", "m@c.com"));
        assertEquals(List.of(Role.EMPLOYEE, Role.TECHNICIAN, Role.ADMINISTRATOR),
                     users.stream().map(User::getRole).toList());
    }

    @Test void onlyAdministratorsSeeAllIncidents() {
        assertFalse(new Employee("E1", "Anu", "a@c.com", "HR").canViewAllIncidents());
        assertTrue(new Administrator("A1", "Meena", "m@c.com").canViewAllIncidents());
    }
}