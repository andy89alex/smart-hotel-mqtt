package com.example.smarthotel.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public final class Json {
    public static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private Json() {}

    public static byte[] toBytes(Object o) {
        try { return MAPPER.writeValueAsBytes(o); }
        catch (Exception e) { throw new RuntimeException(e); }
    }
    public static <T> T read(byte[] data, Class<T> type) {
        try { return MAPPER.readValue(data, type); }
        catch (Exception e) { throw new RuntimeException(e); }
    }
}
