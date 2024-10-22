package com.precognox.ceu.legislative_data_collector.common;

import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.pool2.ObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
public class ChromeBrowserPool {

    @Delegate
    private final ObjectPool<ChromeDriver> browserPool = new GenericObjectPool<>(
            new ChromeBrowserFactory(createChromeOptions(false)), getPoolConfig()
    );

    private ChromeOptions createChromeOptions(boolean isHeadless) {
        ChromeOptions chromeOptions = new ChromeOptions();
        if (isHeadless) {
            chromeOptions.addArguments(new String[]{"--headless"});
            chromeOptions.addArguments(new String[]{"--headless=new"});
        }
        return chromeOptions;
    }

    private GenericObjectPoolConfig<ChromeDriver> getPoolConfig() {
        GenericObjectPoolConfig<ChromeDriver> poolConfig = new GenericObjectPoolConfig<>();
        poolConfig.setMaxWait(Duration.ofSeconds(10));

        return poolConfig;
    }

    public void safeReturn(ChromeDriver obj) {
        try {
            browserPool.returnObject(obj);
        } catch (Exception e) {
            log.error("Error when returning object to pool", e);
        }
    }
}
