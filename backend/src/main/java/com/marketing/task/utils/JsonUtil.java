package com.marketing.task.utils;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * JSON 工具类，基于 Jackson 实现。
 * 提供对象与 JSON 字符串、字节数组之间的转换，支持泛型、集合、Map 等类型。
 * 默认忽略 null 字段，忽略未知属性，支持 Java 8 时间类型。
 */
public final class JsonUtil {

    private JsonUtil() {
        // 工具类禁止实例化
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(JsonUtil.class);

    // 标准 ObjectMapper，不处理根节点包装
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    // 支持根节点包装的 ObjectMapper（通过注解 @JsonRootName 启用）
    private static final ObjectMapper WRAP_OBJECT_MAPPER = new ObjectMapper();

    static {
        // 配置标准 ObjectMapper
        configureMapper(OBJECT_MAPPER, false);
        // 配置支持根节点包装的 ObjectMapper
        configureMapper(WRAP_OBJECT_MAPPER, true);
        WRAP_OBJECT_MAPPER.enable(DeserializationFeature.UNWRAP_ROOT_VALUE);
        WRAP_OBJECT_MAPPER.enable(SerializationFeature.WRAP_ROOT_VALUE);
    }

    private static void configureMapper(ObjectMapper mapper, boolean wrapRoot) {
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        mapper.registerModule(new JavaTimeModule());
        // 仅包装模式下启用根节点相关特性，在调用方单独设置
    }

    private static ObjectMapper getMapper(boolean wrapRoot) {
        return wrapRoot ? WRAP_OBJECT_MAPPER : OBJECT_MAPPER;
    }

    // ==================== 对象与 JSON 字符串互转（标准） ====================

    /**
     * 将 JSON 字符串转换为指定类型的对象。
     *
     * @param jsonString JSON 字符串
     * @param clazz      目标类型
     * @param <T>        目标类型泛型
     * @return 转换后的对象，若输入为空或转换失败则返回 null
     */
    public static <T> T jsonToObj(String jsonString, Class<T> clazz) {
        if (StringUtils.isBlank(jsonString)) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(jsonString, clazz);
        } catch (Exception e) {
            LOGGER.error("jsonToObj error, jsonString: {}", jsonString, e);
            return null;
        }
    }

    /**
     * 将 JSON 字符串转换为指定类型的对象，转换失败时抛出异常（不吞异常）。
     *
     * @param jsonString JSON 字符串
     * @param clazz      目标类型
     * @param <T>        目标类型泛型
     * @return 转换后的对象
     * @throws JsonProcessingException 解析失败或 IO 异常时抛出
     */
    public static <T> T jsonToObjV2(String jsonString, Class<T> clazz) throws JsonProcessingException {
        if (StringUtils.isBlank(jsonString)) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(jsonString, clazz);
        } catch (JsonProcessingException e) {
            LOGGER.error("jsonToObjV2 error, jsonString: {}", jsonString, e);
            throw e;
        }
    }

    /**
     * 将 JSON 字符串转换为指定类型引用的对象（支持泛型，如 List&lt;String&gt;）。
     *
     * @param jsonString    JSON 字符串
     * @param typeReference 类型引用
     * @param <T>           目标类型泛型
     * @return 转换后的对象，若输入为空或转换失败则返回 null
     */
    public static <T> T jsonToObj(String jsonString, TypeReference<T> typeReference) {
        if (StringUtils.isBlank(jsonString)) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(jsonString, typeReference);
        } catch (Exception e) {
            LOGGER.error("jsonToObj error, jsonString: {}", jsonString, e);
            return null;
        }
    }

    /**
     * 将 JSON 字符串转换为指定 JavaType 的对象（高级用法）。
     *
     * @param jsonString JSON 字符串
     * @param javaType   Java 类型描述
     * @param <T>        目标类型泛型
     * @return 转换后的对象，失败或输入为空返回 null
     */
    public static <T> T jsonToObj(String jsonString, JavaType javaType) {
        if (StringUtils.isBlank(jsonString) || javaType == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(jsonString, javaType);
        } catch (Exception e) {
            LOGGER.error("jsonToObj error, jsonString: {}", jsonString, e);
            return null;
        }
    }

    /**
     * 将对象转换为 JSON 字符串。
     *
     * @param obj 待转换的对象
     * @return JSON 字符串，若对象为 null 或转换失败则返回 null
     */
    public static String objToJson(Object obj) {
        if (Objects.isNull(obj)) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            LOGGER.error("objToJson error", e);
            return null;
        }
    }

    /**
     * 将对象转换为格式化后的 JSON 字符串（便于调试）。
     *
     * @param obj 待转换的对象
     * @return 格式化后的 JSON 字符串，失败或输入为空返回 null
     */
    public static String toPrettyJson(Object obj) {
        if (Objects.isNull(obj)) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            LOGGER.error("toPrettyJson error", e);
            return null;
        }
    }

    // ==================== 支持根节点包装的互转 ====================

    /**
     * 将 JSON 字符串转换为指定类型的对象，可选是否解析根节点（需配合 @JsonRootName 使用）。
     *
     * @param jsonStr  JSON 字符串
     * @param clz      目标类型
     * @param rootname 是否启用根节点包装（true：启用；false：不启用）
     * @return 转换后的对象，失败或输入为空返回 null
     */
    public static Object jsonToObj(String jsonStr, Class<?> clz, boolean rootname) {
        if (StringUtils.isBlank(jsonStr)) {
            return null;
        }
        try {
            return getMapper(rootname).readValue(jsonStr, clz);
        } catch (Exception e) {
            LOGGER.error("jsonToObj error, rootname: {}, jsonStr: {}", rootname, jsonStr, e);
            return null;
        }
    }

    /**
     * 将对象转换为 JSON 字符串，可选是否添加根节点（需配合 @JsonRootName 使用）。
     *
     * @param obj      待转换对象
     * @param rootname 是否启用根节点包装
     * @return JSON 字符串，失败或输入为空返回 null
     */
    public static String objToJson(Object obj, boolean rootname) {
        if (Objects.isNull(obj)) {
            return null;
        }
        try {
            return getMapper(rootname).writeValueAsString(obj);
        } catch (Exception e) {
            LOGGER.error("objToJson error, rootname: {}", rootname, e);
            return null;
        }
    }

    // ==================== 兼容旧方法（委托至新方法，便于平滑升级） ====================

    /**
     * @deprecated 请使用 {@link #jsonToObj(String, Class)}
     */
    @Deprecated
    public static <T> T json2Pojo(String jsonString, Class<T> clazz) {
        return jsonToObj(jsonString, clazz);
    }

    /**
     * @deprecated 请使用 {@link #jsonToObj(String, TypeReference)}
     */
    @Deprecated
    public static <T> T json2Pojo(String jsonString, TypeReference<T> typeReference) {
        return jsonToObj(jsonString, typeReference);
    }

    /**
     * @deprecated 请使用 {@link #jsonToObj(String, TypeReference)}，此方法不判空且日志不同，为兼容保留
     */
    @Deprecated
    public static <T> T json2PojoV2(String jsonString, TypeReference<T> typeReference) {
        try {
            return OBJECT_MAPPER.readValue(jsonString, typeReference);
        } catch (Exception e) {
            LOGGER.error("json2PojoV2 error, jsonString: {}", jsonString, e);
            return null;
        }
    }

    // ==================== JSON 转集合 / Map ====================

    /**
     * 将 JSON 字符串转换为指定元素类型的 List。
     *
     * @param jsonStr JSON 字符串
     * @param clazz   元素类型
     * @param <T>     元素类型泛型
     * @return 转换后的 List，失败或输入为空返回空列表（不可变，但通常够用）
     */
    public static <T> List<T> convertJson2List(String jsonStr, Class<T> clazz) {
        if (StringUtils.isBlank(jsonStr)) {
            return Collections.emptyList();
        }
        JavaType javaType = OBJECT_MAPPER.getTypeFactory().constructCollectionType(ArrayList.class, clazz);
        try {
            return OBJECT_MAPPER.readValue(jsonStr, javaType);
        } catch (IOException e) {
            LOGGER.error("convertJson2List error, jsonStr: {}", jsonStr, e);
            return Collections.emptyList();
        }
    }

    /**
     * 将 JSON 字符串转换为 Map&lt;String, String&gt;，转换失败返回空 Map。
     *
     * @param jsonString JSON 字符串
     * @return 转换后的 Map，失败或输入为空返回空 Map（不可变）
     */
    public static Map<String, String> json2Map(String jsonString) {
        if (StringUtils.isBlank(jsonString)) {
            return Collections.emptyMap();
        }
        try {
            return OBJECT_MAPPER.readValue(jsonString, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            LOGGER.error("json2Map error, jsonString: {}", jsonString, e);
            return Collections.emptyMap();
        }
    }

    /**
     * 将 JSON 字符串转换为 Map&lt;String, String&gt;，转换失败抛出异常。
     *
     * @param jsonString JSON 字符串
     * @return 转换后的 Map
     * @throws IllegalArgumentException 如果输入为空
     * @throws JsonProcessingException  JSON 解析失败时抛出
     */
    public static Map<String, String> json2MapV2(String jsonString) throws JsonProcessingException {
        if (StringUtils.isBlank(jsonString)) {
            throw new IllegalArgumentException("JSON 字符串不能为空");
        }
        try {
            return OBJECT_MAPPER.readValue(jsonString, new TypeReference<Map<String, String>>() {});
        } catch (JsonProcessingException e) {
            LOGGER.error("json2MapV2 error, jsonString: {}", jsonString, e);
            throw e;
        }
    }

    /**
     * 将 JSON 字符串转换为嵌套 Map（LinkedHashMap 结构），常用于层级不固定的 JSON。
     *
     * @param jsonString JSON 字符串
     * @return 嵌套 Map，失败或输入为空返回空 LinkedHashMap（可变）
     */
    public static LinkedHashMap<String, LinkedHashMap<String, String>> json2NestedMap(String jsonString) {
        if (StringUtils.isBlank(jsonString)) {
            return new LinkedHashMap<>();
        }
        try {
            return OBJECT_MAPPER.readValue(jsonString, new TypeReference<LinkedHashMap<String, LinkedHashMap<String, String>>>() {});
        } catch (Exception e) {
            LOGGER.error("json2NestedMap error, jsonString: {}", jsonString, e);
            return new LinkedHashMap<>();
        }
    }

    // ==================== JsonNode 相关操作 ====================

    /**
     * 将 JSON 字符串解析为 JsonNode。
     *
     * @param str JSON 字符串
     * @return JsonNode，若输入为 null 或解析失败则返回 null
     */
    public static JsonNode strToJsonNode(String str) {
        if (str == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readTree(str);
        } catch (IOException e) {
            LOGGER.error("strToJsonNode error, str: {}", str, e);
            return null;
        }
    }

    /**
     * 判断一个字符串是否为合法的 JSON。
     *
     * @param str 待判断字符串
     * @return true 表示是合法 JSON，false 表示不是
     */
    public static boolean isJson(String str) {
        if (StringUtils.isBlank(str)) {
            return false;
        }
        try {
            OBJECT_MAPPER.readTree(str);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 创建一个空的 ObjectNode。
     *
     * @return ObjectNode 实例
     */
    public static ObjectNode createObjectNode() {
        return OBJECT_MAPPER.createObjectNode();
    }

    /**
     * 将对象转换为 JsonNode。
     *
     * @param obj 待转换对象
     * @return JsonNode，若 obj 为 null 则返回 null
     */
    public static JsonNode objToJsonNode(Object obj) {
        if (obj == null) {
            return null;
        }
        return OBJECT_MAPPER.valueToTree(obj);
    }

    // ==================== 字节数组转换 ====================

    /**
     * 将对象序列化为 JSON 字节数组。
     *
     * @param object 待序列化对象
     * @return JSON 字节数组，若对象为 null 或序列化失败则返回 null
     */
    public static byte[] toJsonBytes(Object object) {
        if (object == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsBytes(object);
        } catch (JsonProcessingException e) {
            LOGGER.error("toJsonBytes error", e);
            return null;
        }
    }
}