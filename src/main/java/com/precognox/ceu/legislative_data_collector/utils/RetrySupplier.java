package com.precognox.ceu.legislative_data_collector.utils;

@FunctionalInterface
public interface RetrySupplier<T, E extends Throwable> {

    T get() throws E;

}
