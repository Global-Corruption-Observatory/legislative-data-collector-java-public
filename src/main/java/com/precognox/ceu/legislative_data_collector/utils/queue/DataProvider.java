package com.precognox.ceu.legislative_data_collector.utils.queue;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.function.Supplier;

@Slf4j
public class DataProvider<T> {

    @Getter
    private final ExecutorService executorService;
    boolean isFinished = false;
    int packageSize = 50;
    Supplier<Collection<T>> callable;

    @Setter
    Function<Collection<T>, Boolean> addItemsFunction;
    private Future<?> poller;

    public DataProvider(int packageSize, Supplier<Collection<T>> callable, ExecutorService executorService) {
        this.executorService = executorService;
        this.packageSize = packageSize;
        this.callable = callable;
    }

    public DataProvider(InfinityBrowser<List<T>> infinityBrowser, ExecutorService executorService) {
        this.executorService = executorService;
        this.packageSize = infinityBrowser.getPackageSize();
        this.callable = () -> infinityBrowser.poll();
    }

    public synchronized void poll(int currentSize) {
            if (!isFinished) {
                if (currentSize < packageSize) {
                    execute();
                }
                if (currentSize == 0) {
                    waitForLoadData();
                }
        }
    }

    private void waitForLoadData() {
        try {
            log.info("start waitForLoadData");
            if(poller != null) {
                poller.get();
            }
            log.info("end waitForLoadData");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void execute() {
        if (poller == null || poller.isDone()) {
            poller = executorService.submit(() -> {
                log.debug("thread start");
                Collection collection = callable.get();
                if (collection == null || collection.isEmpty()) {
                    isFinished = true;
                } else {
                    this.packageSize = collection.size();
                    addItemsFunction.apply(collection);
                }
                log.debug("thread end");
            });
        }
    }

}
