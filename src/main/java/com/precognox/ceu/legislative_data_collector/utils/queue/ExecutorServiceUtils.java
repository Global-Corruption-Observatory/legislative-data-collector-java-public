package com.precognox.ceu.legislative_data_collector.utils.queue;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Slf4j
public class ExecutorServiceUtils {

    public static <T> void forEach(Iterable<T> iterable, int threadCount, Consumer<? super T> action) {
        if (iterable == null || !iterable.iterator().hasNext()) {
            return;
        }
        if (threadCount < 1) {
            throw new UnsupportedOperationException("threadCount="+threadCount);
        }
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        iterable.forEach(t -> executorService.submit(() -> action.accept(t)));
        try {
            executorService.shutdown();
            executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public static void waitForCompletion(ExecutorService executor) {
        try {
            executor.shutdown();

            String logMsg = executor.awaitTermination(1, TimeUnit.DAYS)
                    ? "Finished processing"
                    : "Aborted processing, timeout elapsed";

            log.info(logMsg);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

}
