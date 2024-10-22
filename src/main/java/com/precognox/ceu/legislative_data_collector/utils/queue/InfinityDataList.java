package com.precognox.ceu.legislative_data_collector.utils.queue;

import lombok.Getter;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class InfinityDataList<T> extends LinkedList<T> {

    @Getter
    private DataProvider<T> dataProvider;
    private ExecutorService executorService;

    public InfinityDataList(ExecutorService executorService, InfinityBrowser<List<T>> infinityBrowser) {
        this(new DataProvider<>(infinityBrowser, executorService));
    }

    public InfinityDataList(ExecutorService executorService, int packageSize, Supplier<Collection<T>> callable) {
        this(new DataProvider<>(packageSize, callable, executorService));
    }

    public InfinityDataList(DataProvider<T> dataProvider) {
        super();
        this.dataProvider = dataProvider;
        this.dataProvider.setAddItemsFunction(collection -> addAll(collection));
        this.executorService = this.dataProvider.getExecutorService();
    }

    @Override
    public int size() {
        dataProvider.poll(super.size());
        return super.size();
    }

    @Override
    public synchronized T poll() {
        size();
        T poll = super.poll();
        return poll;
    }

    @Override
    public Iterator<T> iterator() {
        return new Iterator<T>() {
            private T nextItem;

            @Override
            public boolean hasNext() {
                nextItem = poll();
                return nextItem != null;
            }

            @Override
            public T next() {
                return nextItem;
            }
        };
    }

    @Override
    public Spliterator<T> spliterator() {
        return Spliterators.spliterator(this, Spliterator.CONCURRENT);
//        return Spliterators.spliteratorUnknownSize(iterator(), Spliterator.CONCURRENT);
    }

    @Override
    public void forEach(Consumer<? super T> action) {
        super.forEach(t -> this.executorService.submit(() -> action.accept(t)));
        try {
            executorService.shutdown();
            executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
