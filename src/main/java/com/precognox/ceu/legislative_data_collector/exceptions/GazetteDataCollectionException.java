package com.precognox.ceu.legislative_data_collector.exceptions;

public class GazetteDataCollectionException extends DataCollectionException {
    private final String gazetteUrl;

    public GazetteDataCollectionException(String cause) {
        super(cause);
        this.gazetteUrl = null;
    }

    public GazetteDataCollectionException(String cause, String gazetteUrl) {
        super(cause);
        this.gazetteUrl = gazetteUrl;
    }

    public GazetteDataCollectionException(String message, Throwable cause) {
        super(message, cause);
        this.gazetteUrl = null;
    }

    public GazetteDataCollectionException(String message, Throwable cause, String gazetteUrl) {
        super(message, cause);
        this.gazetteUrl = gazetteUrl;
    }

    public String getUrl() {
        return this.gazetteUrl;
    }
}
