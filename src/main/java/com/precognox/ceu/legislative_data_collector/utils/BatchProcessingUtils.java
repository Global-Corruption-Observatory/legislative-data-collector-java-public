package com.precognox.ceu.legislative_data_collector.utils;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.IntStream;

@Slf4j
public class BatchProcessingUtils {


    public static <T> void completeInBatches(Consumer<List<T>> task, List<T> inputs, int batchSize) {
        int batchCount = inputs.size() / batchSize + 1;
        IntStream.range(0, batchCount)
                .peek(i -> log.info("Starting work on batch: {} of {}", i + 1, batchCount))
                .mapToObj(i -> getNthBatch(inputs, batchSize, i))
                .peek(batch -> log.info("Size of batch is {}", batch.size()))
                .forEach(task);
    }

    private static <T> List<T> getNthBatch(List<T> inputs, int batchSize, int n) {
        return inputs.stream()
                .skip(n * batchSize)
                .limit(batchSize)
                .toList();
    }
}
