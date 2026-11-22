package com.iitsm.iitsm.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.servlet.ModelAndView;

/** PDF exception list: "Database failure" is handled with a clear 503 page, never a crash. */
class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void databaseUnreachableShowsTheUnavailablePage() {
        ModelAndView page = handler.databaseUnavailable(new DataAccessResourceFailureException("Connection refused"));

        assertEquals("error", page.getViewName());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, page.getStatus());
        assertEquals(503, page.getModel().get("status"));
    }

    @Test
    void failingToStartATransactionIsTreatedTheSame() {
        ModelAndView page = handler.databaseUnavailable(new CannotCreateTransactionException("No connection"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, page.getStatus());
    }
}
