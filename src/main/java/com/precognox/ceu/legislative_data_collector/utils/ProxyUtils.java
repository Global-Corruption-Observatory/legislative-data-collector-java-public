package com.precognox.ceu.legislative_data_collector.utils;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.chrome.ChromeOptions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

@Slf4j
public class ProxyUtils {

    private static List<String> proxyList = new ArrayList<>(Arrays.asList(
            "116.203.135.100:1201",
            "116.203.135.100:1202",
            "116.203.135.100:1203",
            "116.203.135.100:1204",
            "116.203.135.100:1205",
            "116.203.135.100:1206",
            "116.203.135.100:1207",
            "116.203.135.100:1208",
            "116.203.135.100:1209",
            "116.203.135.100:1210"
    ));

    public static Connection setProxy(Connection connection) {
        if (proxyList != null && !proxyList.isEmpty()) {
            return setupConnectionProxy(connection, proxyList);
        }
        return connection;
    }

    private static Connection setupConnectionProxy(Connection c, List<String> proxyList) {
        Random random = new Random();
        String proxyText = proxyList.get(random.nextInt(proxyList.size()));
//        log.info("Current proxy: " + proxyText);
        String[] proxyWithPort = proxyText.split(":");
        return c.proxy(proxyWithPort[0], Integer.parseInt(proxyWithPort[1]));
    }

    public static void setProxy(ChromeOptions options) {
        if (proxyList != null && !proxyList.isEmpty()) {
            Proxy proxy = new Proxy();
            Random random = new Random();
            String proxyText = proxyList.get(random.nextInt(proxyList.size()));
//            log.info("Current proxy: " + proxyText);
            proxy.setHttpProxy(proxyText);
            proxy.setSslProxy(proxyText);
            options.setProxy(proxy);
        }
    }
}
