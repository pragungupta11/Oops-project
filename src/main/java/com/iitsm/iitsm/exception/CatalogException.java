package com.iitsm.iitsm.exception;

/** Invalid change to the service or category catalogue (duplicate name, bad criticality, ...). */
public class CatalogException extends RuntimeException {
    public CatalogException(String message) {
        super(message);
    }
}
