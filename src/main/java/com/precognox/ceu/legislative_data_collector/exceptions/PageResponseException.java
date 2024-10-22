package com.precognox.ceu.legislative_data_collector.exceptions;

public class PageResponseException extends DataCollectionException{

    public PageResponseException(String cause) {
        super(cause);
    }

    public PageResponseException(String message, Throwable cause) {
        super(message, cause);
    }
}
