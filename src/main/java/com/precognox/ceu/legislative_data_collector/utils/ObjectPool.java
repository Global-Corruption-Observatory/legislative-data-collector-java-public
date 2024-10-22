package com.precognox.ceu.legislative_data_collector.utils;

import org.apache.commons.pool2.impl.AbandonedConfig;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class ObjectPool<T> extends GenericObjectPool<T> {

    public ObjectPool(Supplier<T> createSupplier, Consumer<T> closeConsumer, int maxTotal) {
        super(new PooledObjectFactory<>(createSupplier, closeConsumer), getGenericObjectPoolConfig(maxTotal));
    }

    public ObjectPool(PooledObjectFactory<T> factory, int maxTotal) {
        super(factory, getGenericObjectPoolConfig(maxTotal));
    }

    public ObjectPool(PooledObjectFactory<T> factory) {
        super(factory);
    }

    public ObjectPool(PooledObjectFactory<T> factory, GenericObjectPoolConfig<T> config) {
        super(factory, config);
    }

    public ObjectPool(PooledObjectFactory<T> factory, GenericObjectPoolConfig<T> config, AbandonedConfig abandonedConfig) {
        super(factory, config, abandonedConfig);
    }

    private static <T> GenericObjectPoolConfig<T> getGenericObjectPoolConfig(int maxTotal) {
        GenericObjectPoolConfig<T> conf = new GenericObjectPoolConfig<>();
        conf.setMaxTotal(maxTotal);
        return conf;
    }

    @Override
    public T borrowObject() {
        try {
            return super.borrowObject();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
