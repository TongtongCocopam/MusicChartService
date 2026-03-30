package kongju.musicchartservice.global.common;

import lombok.*;
import org.springframework.web.ErrorResponse;

import kongju.musicchartservice.global.error.ErrorCode;


@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ApiResponse<T> {
    private boolean success;
    private T data;
    private String message;


    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(
                true,
                data,
                null
        );
    }

    public static <T> ApiResponse<T> success() {
        return new ApiResponse<>(
                true,
                null,
                null
        );
    }

    public static ApiResponse<Void> fail(ErrorCode errorCode) {
        return new ApiResponse<>(
                false,
                null,
                errorCode.getMessage()
        );
    }

    public static ApiResponse<Void> fail(String message) {
        return new ApiResponse<>(
                false,
                null,
                message
        );
    }
}
