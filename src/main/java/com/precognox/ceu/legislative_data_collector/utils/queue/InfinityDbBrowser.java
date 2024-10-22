package com.precognox.ceu.legislative_data_collector.utils.queue;

import lombok.Setter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class InfinityDbBrowser<T> implements InfinityBrowser<List<T>> {

    int pageSize = 10;
    @Setter
    int lastPage = -1;
    Pageable pageable;
    Function<Pageable, Page<T>> pageFunction;

    public InfinityDbBrowser(Function<Pageable, Page<T>> pageFunction) {
        this.pageFunction = pageFunction;
        pageable = PageRequest.of(0, pageSize);
    }

    public InfinityDbBrowser(int pageSize, Function<Pageable, Page<T>> pageFunction) {
        this.pageSize = pageSize;
        this.pageFunction = pageFunction;
        pageable = PageRequest.of(0, pageSize);
    }

    public InfinityDbBrowser(int currentPage, int pageSize, Function<Pageable, Page<T>> pageFunction) {
        this.pageSize = pageSize;
        this.pageFunction = pageFunction;
        pageable = PageRequest.of(currentPage, pageSize);
    }

    public void setCurrentPage(int currentPage) {
        pageable = PageRequest.of(currentPage, pageSize);
    }

    @Override
    public List<T> poll() {
        if (pageable == null) {
            return new ArrayList<>();
        }
        if (lastPage > -1 && lastPage < pageable.getPageNumber()) {
            return new ArrayList<>();
        }
        Page<T> page = pageFunction.apply(pageable);
        if(page.hasNext()) {
            pageable = page.nextPageable();
        } else {
            pageable = null;
        }
        return page.getContent();
    }

    public int getPackageSize() {
        return pageSize;
    }
}
