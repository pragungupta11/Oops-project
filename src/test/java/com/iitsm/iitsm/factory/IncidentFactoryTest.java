package com.iitsm.iitsm.factory;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import org.junit.jupiter.api.Test;

import com.iitsm.iitsm.exception.InvalidIncidentDataException;
import com.iitsm.iitsm.model.*;

class IncidentFactoryTest {
    private final IncidentFactory factory = new IncidentFactory();
    private final User reporter = new Employee("E1", "Anu", "anu@c.com", "Finance");
    private final ItService payment = new ItService("Payment Gateway", 3, Set.of("Database"));

    @Test void buildsIncidentWithDefaultsAndServiceCriticality() {
        Incident i = factory.create(reporter, "Payment down", Category.APPLICATION, 3, 2, payment);
        assertEquals("Application", i.getCategory());
        assertEquals(3, i.getCriticality());
        assertEquals(IncidentStatus.NEW, i.getStatus());
        assertSame(reporter, i.getReporter());
    }

    @Test void idsAreUnique() {
        Incident a = factory.create(reporter, "x", Category.NETWORK, 1, 1, payment);
        Incident b = factory.create(reporter, "y", Category.NETWORK, 1, 1, payment);
        assertNotEquals(a.getId(), b.getId());
    }

    @Test void rejectsBlankDescription() {
        assertThrows(InvalidIncidentDataException.class,
            () -> factory.create(reporter, "  ", Category.NETWORK, 1, 1, payment));
    }

    @Test void rejectsOutOfRangeImpact() {
        assertThrows(InvalidIncidentDataException.class,
            () -> factory.create(reporter, "x", Category.NETWORK, 5, 1, payment));
    }
}