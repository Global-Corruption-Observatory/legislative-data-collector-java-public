package com.precognox.ceu.legislative_data_collector.utils;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.PathNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Slf4j
public class JsonPathUtils {

    protected static final Logger logger = LoggerFactory.getLogger(JsonPathUtils.class.getName());

    public static <T> T toObject(Object data) {
        return getNestedObject(data, "$", null);
    }

    public static <T> List<T> toList(Object data) {
        return getNestedObject(data, "$", Collections.emptyList());
    }

    public static <T> T getNestedObject(Object data, String path) {
        return getNestedObject(data, path, null);
    }

    public static <T> T getNestedObject(Object data, String path, T defaultValue) {
        if (data == null) {
            return defaultValue;
        }
        String jsonPath = path.startsWith("$") ? path : "$.." + path;
        T ret = JsonPathUtils.findByJsonPath(parse(data), jsonPath, defaultValue);
        return ret != null ? ret : defaultValue;
    }

    public static DocumentContext parseJson(String data) {
        try {
//        return JsonPath.using(Configuration.defaultConfiguration()).parse(data);
            Configuration configuration = Configuration.defaultConfiguration()
                    .addOptions(Option.SUPPRESS_EXCEPTIONS);
            return JsonPath.using(configuration).parse(data);
        } catch (Exception e) {
            logger.error(String.format("Failed to parse Json:%s;", data));
            throw new RuntimeException(e);
        }
    }

    public static DocumentContext parse(Object data) {
        if (data instanceof DocumentContext) {
            return (DocumentContext) data;
        }
        if (data instanceof String) {
            return parseJson((String) data);
        }
        return JsonPath.using(Configuration.defaultConfiguration()).parse(data);
    }

    public static String findTextInObject(Object data, String jsonPath) {
        return findText(parse(data), jsonPath, null);
    }

    public static String findText(DocumentContext context, String jsonPath) {
        return findText(context, jsonPath, null);
    }

    public static String findText(DocumentContext context, String jsonPath, String defaultValue) {
        return Objects.toString(getFirstObjectIfList(findByJsonPath(context, jsonPath)), defaultValue);
    }

    public static Object getFirstObjectIfList(Object o) {
        if (o instanceof List) {
            List list = (List) o;
            if (list.isEmpty()) {
                return null;
            } else {
                return getFirstObjectIfList(list.get(0));
            }
        }
        return o;
    }

    public static <T> T findByJsonPath(DocumentContext context, String jsonPath) {
        return findByJsonPath(context, jsonPath, null);
    }

    public static <T> T findByJsonPath(DocumentContext context, String jsonPath, T defaultValue) {
        try {
            Object o = context.read(jsonPath);
            if (o == null) {
                return defaultValue;
            }
            if (defaultValue != null && defaultValue instanceof List) {
                if(o != null && o instanceof List) {
                    List list = (List) o;
                    if (!list.isEmpty() && list.get(0) instanceof List) {
                        List ret = new ArrayList();
                        for (Object item : list) {
                            if (item instanceof List) {
                                ret.addAll((List) item);
                            } else {
                                ret.add(item);
                            }
                        }
                        return (T) ret;
                    } else {
                        return (T) o;
                    }
                } else {
                    List ret = new ArrayList();
                    ret.add(o);
                    return (T) ret;
                }
            } else if(o != null && o instanceof List) {
                List list = (List) o;
                if (list.size() == 1) {
                    return (T) list.get(0);
                }
            }
            return (T) o;
        } catch (PathNotFoundException e) {
            logger.warn("Failed to read data", e);
            return defaultValue;
        } catch (ClassCastException e) {
            logger.warn("Failed to Cast read data", e);
            return defaultValue;
        }
    }

    public static <T> List<T> findListByJsonPath(Object data, String jsonPath) {
        return findListByJsonPath(parse(data), jsonPath);
    }

    public static <T> List<T> findListByJsonPath(DocumentContext context, String jsonPath) {
        Object obj = findByJsonPath(context, jsonPath, new ArrayList<>());
        if(!(obj instanceof List)) {
            return List.of( (T) obj);
        }
        return (List) obj;
    }

    public static <T> T read(Object data, String jsonPath) {
        return findByJsonPath(parse(data), jsonPath, null);
    }

}
