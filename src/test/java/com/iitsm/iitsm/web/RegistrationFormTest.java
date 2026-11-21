package com.iitsm.iitsm.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

@SpringBootTest
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:registrationformtest")
class RegistrationFormTest {

    @Autowired RegistrationController controller;

    @Test
    void everyMissingFieldIsListedAtOnce() {
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.register("Priya Shah", "", null, "", "", model, new RedirectAttributesModelMap());

        assertEquals("register", view);
        String error = (String) model.getAttribute("error");
        assertTrue(error.contains("Company e-mail") && error.contains("Username")
                && error.contains("Password") && error.contains("Confirm password"), error);
        assertEquals(List.of("email", "username", "password", "confirm"), model.getAttribute("missing"));
        assertEquals("Priya Shah", model.getAttribute("fullName"));   // what was typed is kept
    }
}
