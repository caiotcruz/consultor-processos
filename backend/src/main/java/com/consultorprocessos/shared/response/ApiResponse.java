package com.consultorprocessos.shared.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean     success,
        T           data,
        ErrorDetail error,
        PageMeta    meta
) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, null);
    }

    public static <T> ApiResponse<java.util.List<T>> success(
                org.springframework.data.domain.Page<T> page) {
        return new ApiResponse<>(
                true,
                page.getContent(),
                null,
                new PageMeta(
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
                )
        );
        }

    public static ApiResponse<Void> error(String code, String message) {
        return new ApiResponse<>(false, null,
                new ErrorDetail(code, message, List.of()), null);
    }

    public static ApiResponse<Void> validationError(List<FieldErrorDetail> details) {
        return new ApiResponse<>(false, null,
                new ErrorDetail("VALIDATION_ERROR",
                        "Um ou mais campos são inválidos.", details), null);
    }

    public record ErrorDetail(
            String               code,
            String               message,
            List<FieldErrorDetail> details
    ) {}

    public record FieldErrorDetail(String field, String message) {}

    public record PageMeta(int page, int pageSize, long totalElements, int totalPages) {}
}