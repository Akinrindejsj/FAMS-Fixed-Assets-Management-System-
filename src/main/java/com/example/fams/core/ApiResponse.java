package com.example.fams.core;

import java.time.LocalDateTime;

public class ApiResponse<T> {
    private boolean success;
    private String message;
    private T data;
    private ErrorDetail error;
    private LocalDateTime timestamp;

    public ApiResponse(boolean success, String message, T data) {
        this.success = success;
        this.message = message;
        this.data = data;
        this.timestamp = LocalDateTime.now();
    }

    public ApiResponse(boolean success, String message, ErrorDetail error) {
        this.success = success;
        this.message = message;
        this.error = error;
        this.timestamp = LocalDateTime.now();
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data);
    }

    public static <T> ApiResponse<T> error(String message, String errorCode, String errorDescription) {
        return new ApiResponse<>(false, message, new ErrorDetail(errorCode, errorDescription));
    }

    public static <T> ApiResponse<T> error(String message, Throwable exception) {
        String errorCode = exception.getClass().getSimpleName();
        String errorDescription = exception.getMessage() != null ? exception.getMessage() : "An unexpected error occurred";
        return new ApiResponse<>(false, message, new ErrorDetail(errorCode, errorDescription));
    }

    // Getters and Setters
    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public ErrorDetail getError() {
        return error;
    }

    public void setError(ErrorDetail error) {
        this.error = error;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public static class ErrorDetail {
        private String code;
        private String description;

        public ErrorDetail(String code, String description) {
            this.code = code;
            this.description = description;
        }

        public String getCode() {
            return code;
        }

        public String getDescription() {
            return description;
        }
    }
}

