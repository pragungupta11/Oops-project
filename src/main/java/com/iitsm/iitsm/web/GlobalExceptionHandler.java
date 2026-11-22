package com.iitsm.iitsm.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

/**
 * Database failure (PDF exception list): if the database cannot be reached or a query fails, the
 * user sees a clear "temporarily unavailable" page (HTTP 503) instead of a crash, and the real
 * cause is logged for the administrators. Nothing internal is shown to the user.
 */
@ControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    public ModelAndView databaseUnavailable(RuntimeException e) {
        log.error("Database failure: {}", e.getMessage(), e);
        ModelAndView page = new ModelAndView("error");
        page.setStatus(HttpStatus.SERVICE_UNAVAILABLE);
        page.addObject("status", HttpStatus.SERVICE_UNAVAILABLE.value());
        page.addObject("databaseDown", true);
        return page;
    }
}
