package com.precognox.ceu.legislative_data_collector.common;

import com.jauntium.Browser;
import org.apache.commons.pool2.impl.DefaultPooledObject;

public class PooledBrowser extends DefaultPooledObject<Browser> {

    public PooledBrowser(Browser browser) {
        super(browser);
    }

}
