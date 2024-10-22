package com.precognox.ceu.legislative_data_collector.common;

import com.jauntium.Browser;
import org.apache.commons.pool2.ObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.stereotype.Service;

@Service
public class BrowserPool {

    private final ObjectPool<Browser> browserPool;

    private static final int MAX_INSTANCES = 15;

    public BrowserPool() {
        GenericObjectPoolConfig<Browser> conf = new GenericObjectPoolConfig<>();
        conf.setMaxTotal(MAX_INSTANCES);

        browserPool = new GenericObjectPool<>(new JauntiumBrowserFactory(), conf);
    }

    public Browser get() {
        try {
            return browserPool.borrowObject();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void returnToPool(Browser b) {
        try {
            browserPool.returnObject(b);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
