package com.precognox.ceu.legislative_data_collector.utils.playwright;

import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;

@Slf4j
@NoArgsConstructor
public class PlaywrightFactory extends BasePooledObjectFactory<PlaywrightWrapper> {

    @Override
    public PlaywrightWrapper create() {
        PlaywrightWrapper browser = new PlaywrightWrapper();
        log.info("Driver created successfully");
        return browser;
    }

    @Override
    public PooledObject<PlaywrightWrapper> wrap(PlaywrightWrapper obj) {
        return new DefaultPooledObject<>(obj);
    }

    @Override
    public void destroyObject(PooledObject<PlaywrightWrapper> obj) {
        obj.getObject().close();
    }


}