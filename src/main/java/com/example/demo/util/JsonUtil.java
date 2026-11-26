package com.example.demo.util;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.TimeZone;

import com.fasterxml.jackson.annotation.JsonInclude;
//import com.fasterxml.jackson.annotation.JsonInclude.Include;

import tools.jackson.core.json.JsonReadFeature;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;
//import tools.jackson.databind.module.SimpleModule;

public class JsonUtil {

	private static final ObjectMapper objectMapper;
    static {
//        objectMapper = new ObjectMapper();
//        //去掉默认的时间戳格式
//        objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
//        //设置为中国上海时区
//        objectMapper.setTimeZone(TimeZone.getTimeZone("GMT+8"));
//        objectMapper.configure(SerializationFeature.WRITE_NULL_MAP_VALUES, false);
//        //空值不序列化
//        objectMapper.setSerializationInclusion(Include.NON_NULL);
//        //反序列化时，属性不存在的兼容处理
//        objectMapper.getDeserializationConfig().withoutFeatures(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
//
//        //序列化时，日期的统一格式
//        objectMapper.setDateFormat(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));
//
//        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
//        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
//        //单引号处理
//        objectMapper.configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
//        // 使Jackson JSON支持Unicode编码非ASCII字符
//        SimpleModule module = new SimpleModule();
//        module.addSerializer(String.class, new StringUnicodeSerializer());
//        objectMapper.registerModule(module);
//        
//        SimpleModule module = new SimpleModule();
//        module.addSerializer(String.class, new StringUnicodeSerializer());

    	
        objectMapper = JsonMapper.builder()
                // 1. 去掉时间戳，改为格式化字符串
                // WRITE_DATES_AS_TIMESTAMPS 已从 SerializationFeature 移到 DateTimeFeature
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                // 2. 时区
                .defaultTimeZone(TimeZone.getTimeZone("GMT+8"))
                // 3. 空值不序列化（原 setSerializationInclusion 的替代写法）
                .changeDefaultPropertyInclusion(incl -> incl
                        .withValueInclusion(JsonInclude.Include.NON_NULL)
                        .withContentInclusion(JsonInclude.Include.NON_NULL))
                // 4. 反序列化时忽略不存在的属性（原 withoutFeatures 的兼容写法）
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                // 5. 日期格式
                .defaultDateFormat(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"))
                // 6. 允许空 Bean 序列化
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                // 7. 允许单引号
                .enable(JsonReadFeature.ALLOW_SINGLE_QUOTES)
                // 8. 注册自定义序列化模块
//                .addModule(module)
                .build();
    }
	
	public static <T> String toString(T bean) {
		return objectMapper.writeValueAsString(bean);
	}
	
	public static <T> T toBean(String jsonStr, Class<T> valueType) {
		return objectMapper.readValue(jsonStr, valueType);
	}
	
	public static <T> List<T> toList(String jsonStr, Class<T> valueType) {
//		List<T> list = null;
//        try {
//            JSONArray json = JSONArray.parseArray(content);
//            T t;
//            list = new ArrayList<>();
//            for (Object obj : json) {
//                JSONObject jsonObject = (JSONObject) obj;
//                t = JSONObject.toJavaObject(jsonObject, clazz);
//                list.add(t);
//            }
//        } catch (Exception e) {
//        }
//        return list;
        
		// JSON 数组转 List<DemoReq>
        List<T> list = objectMapper.readValue(jsonStr, new TypeReference<List<T>>() {});
        return list;
        
        // JSON 对象转 Map
//        String jsonObj = "{\"name\":\"张三\", \"age\":18}";
//        Map<String, Object> map = objectMapper.readValue(jsonObj, new TypeReference<Map<String, Object>>() {});
	}
}
