package com.precognox.ceu.legislative_data_collector.common;

import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class ResourceLoader {

    /**
     * Used for testing the page parser classes with stored HTML files. Gets a static HTML page from the test resources folder, and builds a {@link PageSource} object from it.
     *
     * @param file Path of the HTML file under the test resources folder.
     *
     * @return PageSource object with the content of the file.
     *
     * @throws IOException When the resource file is not found.
     */
    @NotNull
    public static PageSource getPageSourceObj(String file) throws IOException {
        PageSource pageSource = new PageSource();
        pageSource.setRawSource(getResourceAsString(file));

        return pageSource;
    }

    public static byte[] getResourceAsBytes(String path) throws IOException {
        try (InputStream stream = ResourceLoader.class.getResourceAsStream(path)) {
            return Objects.requireNonNull(stream).readAllBytes();
        }
    }

    public static String getResourceAsString(String path) throws IOException {
        try (InputStream stream = ResourceLoader.class.getResourceAsStream(path)) {
            Objects.requireNonNull(stream, "Resource not found: " + path);
            return IOUtils.toString(stream, StandardCharsets.UTF_8);
        }
    }

}
