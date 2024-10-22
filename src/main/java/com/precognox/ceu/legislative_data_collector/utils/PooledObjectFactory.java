package com.precognox.ceu.legislative_data_collector.utils;

import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class PooledObjectFactory<T> extends BasePooledObjectFactory<T> {

    private final Supplier<T> createSupplier;
    private Consumer<T> closeConsumer;

    public PooledObjectFactory(Supplier<T> createSupplier) {
        this(createSupplier, null);
    }

    public PooledObjectFactory(Supplier<T> createSupplier, Consumer<T> closeConsumer) {
        this.createSupplier = createSupplier;
        this.closeConsumer = closeConsumer;
    }

    @Override
    public T create() throws Exception {
        return this.createSupplier.get();
    }

    @Override
    public PooledObject<T> wrap(T obj) {
        return new DefaultPooledObject<>(obj);
    }

    @Override
    public void destroyObject(PooledObject<T> obj) throws Exception {
        if (this.closeConsumer != null) {
            this.closeConsumer.accept(obj.getObject());
        } else {
            super.destroyObject(obj);
        }
    }
}
