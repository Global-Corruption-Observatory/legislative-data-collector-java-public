package com.precognox.ceu.legislative_data_collector.utils;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.HttpStatusException;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class RetryUtils {

    private static RetryTemplate retryHttpTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();

        FixedBackOffPolicy fixedBackOffPolicy = new FixedBackOffPolicy();
        fixedBackOffPolicy.setBackOffPeriod(10000);
        retryTemplate.setBackOffPolicy(fixedBackOffPolicy);

        Map<Class<? extends Throwable>, Boolean> retryableExceptions = new HashMap<>();
        retryableExceptions.put(HttpStatusException.class, true);
        retryableExceptions.put(SocketTimeoutException.class, true);

        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(3, retryableExceptions);
        retryTemplate.setRetryPolicy(retryPolicy);

        return retryTemplate;
    }

    public static final <T, E extends IOException> T execute(RetrySupplier<T, E> retrySupplier) throws IOException {
        try {
            return retryHttpTemplate().execute(context -> {
                try {
                    if (context.getRetryCount() > 0) {
                        log.error("Page not responded as expected. Retrying!");
                    }
                    return retrySupplier.get();
                } catch (IOException ex) {
                    if (ex instanceof HttpStatusException) {
                        throw ex;
                    }
                    String exceptionMessage = ex.getMessage();
                    int httpStatusCode = TextUtils.toInteger(TextUtils.findText(exceptionMessage, "(?:Status=|response code:)[\\s]*(\\d{3})"), 0);
                    String pageLink =TextUtils.findText(exceptionMessage, "(?:URL:)[\\s]*([^\\s]+)[\\s]+");
                    log.error("Failed to visit page: {}", pageLink, ex);
                    throw new HttpStatusException("Failed to visit page: ", httpStatusCode, pageLink);
                }
            });
        } catch (IOException ex) {
            if (ex instanceof HttpStatusException) {
                HttpStatusException e = (HttpStatusException) ex;
                log.info("Http status code: {}", e.getStatusCode());
                if (e.getStatusCode() == 429) {
                    log.error("Banned!");
                }
            }
            throw ex;
        }
    }

    public static RetryTemplate getRetryTemplate(int wait, int limit, List<? extends Throwable> exceptions) {
        RetryTemplate retryTemplate = new RetryTemplate();

        FixedBackOffPolicy fixedBackOffPolicy = new FixedBackOffPolicy();
        fixedBackOffPolicy.setBackOffPeriod(wait);
        retryTemplate.setBackOffPolicy(fixedBackOffPolicy);

        Map<Class<? extends Throwable>, Boolean> retryableExceptions = new HashMap<>();
        exceptions.forEach(exception -> retryableExceptions.put(exception.getClass(), true));
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(limit, retryableExceptions);
        retryTemplate.setRetryPolicy(retryPolicy);


        return retryTemplate;
    }
}
