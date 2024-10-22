package com.precognox.ceu.legislative_data_collector.exceptions;

public class DataCollectionException extends Exception{
    public DataCollectionException(String cause) {
        super(cause);
    }

    public DataCollectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
