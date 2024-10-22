package com.precognox.ceu.legislative_data_collector.common;

import lombok.NoArgsConstructor;
import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@NoArgsConstructor
public class ChromeBrowserFactory extends BasePooledObjectFactory<ChromeDriver> {

    private ChromeOptions options = getDefaultOptions();

    public ChromeBrowserFactory(ChromeOptions options) {
        this.options = options;
    }

    private ChromeOptions getDefaultOptions() {
        options = new ChromeOptions();
        options.setImplicitWaitTimeout(Duration.ofSeconds(5));
        options.addArguments("--remote-allow-origins=*");

        return options;
    }

    @Override
    public ChromeDriver create() throws Exception {
        return new ChromeDriver(options);
    }

    @Override
    public PooledObject<ChromeDriver> wrap(ChromeDriver obj) {
        return new DefaultPooledObject<>(obj);
    }

    @Override
    public void destroyObject(PooledObject<ChromeDriver> obj) throws Exception {
        obj.getObject().close();
    }
}
