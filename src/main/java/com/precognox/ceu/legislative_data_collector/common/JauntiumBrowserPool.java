package com.precognox.ceu.legislative_data_collector.common;

import com.jauntium.Browser;
import lombok.experimental.Delegate;
import org.apache.commons.pool2.ObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class JauntiumBrowserPool {

    @Delegate
    private final ObjectPool<Browser> browserPool = new GenericObjectPool<>(
            new JauntiumBrowserFactory(createChromeOptions(false)), getPoolConfig()
    );

    private ChromeOptions createChromeOptions(boolean isHeadless) {
        ChromeOptions chromeOptions = new ChromeOptions();
        if (isHeadless) {
            chromeOptions.addArguments(new String[]{"--headless"});
            chromeOptions.addArguments(new String[]{"--headless=new"});
        }
        return chromeOptions;
    }

    private GenericObjectPoolConfig<Browser> getPoolConfig() {
        GenericObjectPoolConfig<Browser> poolConfig = new GenericObjectPoolConfig<>();
        poolConfig.setMaxWait(Duration.ofSeconds(10));

        return poolConfig;
    }

}
