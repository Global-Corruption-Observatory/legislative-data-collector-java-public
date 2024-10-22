package com.precognox.ceu.legislative_data_collector.utils.queue;

import java.util.Collection;

public interface InfinityBrowser<T extends Collection> {

    T poll();

    int getPackageSize();
}
