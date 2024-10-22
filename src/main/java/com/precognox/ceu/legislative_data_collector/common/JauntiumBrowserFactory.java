package com.precognox.ceu.legislative_data_collector.common;

import com.jauntium.Browser;
import lombok.AllArgsConstructor;
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
@AllArgsConstructor
public class JauntiumBrowserFactory extends BasePooledObjectFactory<Browser> {

    private ChromeOptions options = getDefaultOptions();

    private ChromeOptions getDefaultOptions() {
        options = new ChromeOptions();
        options.setImplicitWaitTimeout(Duration.ofSeconds(5));
        options.addArguments("--remote-allow-origins=*");
        options.setBinary(Constants.CHROME_LOCATION);

        return options;
    }

    @Override
    public Browser create() {
        return new Browser(new ChromeDriver(options));
    }

    @Override
    public PooledObject<Browser> wrap(Browser obj) {
        return new DefaultPooledObject<>(obj);
    }

    @Override
    public void destroyObject(PooledObject<Browser> obj) throws Exception {
        obj.getObject().quit();
    }
}
