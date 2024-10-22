package com.precognox.ceu.legislative_data_collector.exceptions;

public class NotInDatabaseException extends DataCollectionException{

    public NotInDatabaseException(String cause) {
        super(cause);
    }

}