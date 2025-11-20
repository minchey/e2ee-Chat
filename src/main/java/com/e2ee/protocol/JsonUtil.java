package com.e2ee.protocol;

import com.google.gson.Gson;

public class JsonUtil {

    // Gson 하나 만들어두고 계속 재사용
    private static final Gson gson = new Gson();

    // 객체 -> JSON 문자열
    public static String toJson(Object o) {
        return gson.toJson(o);
    }

    // JSON 문자열 -> 객체
    public static <T> T fromJson(String json, Class<T> type) {
        return gson.fromJson(json, type);
    }
}
