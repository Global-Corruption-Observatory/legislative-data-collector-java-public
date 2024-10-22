package com.precognox.ceu.legislative_data_collector.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import kong.unirest.JsonObjectMapper;
import kong.unirest.json.JSONArray;
import kong.unirest.json.JSONObject;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Slf4j
public class JsonUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static <T> T parseToObject(String jsonStr, Class<T> cls) {
        try {
            return OBJECT_MAPPER.readValue(jsonStr, cls);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> T parseToObject(String jsonStr, TypeReference<T> type) {
        try {
            return OBJECT_MAPPER.readValue(jsonStr, type);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public static Stream<JSONObject> toObjectStream(JSONArray array) {
        return IntStream.range(0, array.length()).mapToObj(array::getJSONObject);
    }

    public static List<JSONObject> jsonArrayToObjectList(JSONArray array) {
        return toObjectStream(array).collect(Collectors.toList());
    }

    public static String toString(Object data) {
        try {
            return new JsonObjectMapper().writeValue(data);
        } catch (Exception e) {
            log.error("writeToString", e);
            return "";
        }
    }
}
