package com.iitsm.iitsm.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import com.iitsm.iitsm.strategy.category.FallbackCategorySuggester;

@SpringBootTest
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:categorysuggestiontest")
class CategorySuggestionEndpointTest {

    @Autowired EmployeeController controller;
    @Autowired FallbackCategorySuggester suggester;

    @Test
    void suggestsApplicationForThePaymentOutage() {
        ResponseEntity<EmployeeController.SuggestionResponse> r = controller.suggestCategory("Payment app down");
        assertEquals(HttpStatus.OK, r.getStatusCode());
        assertEquals("Application", r.getBody().category());
        assertEquals("keywords", r.getBody().source());
    }

    @Test
    void answersNoContentWhenThereIsNoGuess() {
        assertEquals(HttpStatus.NO_CONTENT, controller.suggestCategory("Something is weird today").getStatusCode());
    }

    @Test
    void noAiIsConfiguredYet() {
        assertEquals(0, suggester.remoteCount());   // purely rule-based until an AI adapter is added
    }
}
