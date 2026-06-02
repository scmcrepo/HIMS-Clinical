package com.hms.api.shared;
import com.fasterxml.jackson.annotation.JsonInclude;
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(String message, T data) {
    public static <T> ApiResponse<T> ok(String message, T data) { return new ApiResponse<>(message, data); }
    public static ApiResponse<Void> ok(String message) { return new ApiResponse<>(message, null); }
    public static ApiResponse<Void> error(String message) { return new ApiResponse<>(message, null); }
    public static <T> ApiResponse<T> of(String message, T data) { return new ApiResponse<>(message, data); }
    public static <T> ApiResponse<T> of(T data) { return new ApiResponse<>(null, data); }
}
