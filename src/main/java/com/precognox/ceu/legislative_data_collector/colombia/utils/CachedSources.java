package com.precognox.ceu.legislative_data_collector.colombia.utils;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

public class CachedSources<T> {
    private Set<T> cachedSources;

    public CachedSources() {
        this.cachedSources = new HashSet<>();
    }

    public boolean add(T source) {
        return cachedSources.add(source);
    }

    public Stream<T> stream() {
        return cachedSources.stream();
    }

    public <INDEX> void saveToDatabase(JpaRepository<T, INDEX> repository) {
        repository.saveAll(cachedSources);
        cachedSources.clear();
    }
}
