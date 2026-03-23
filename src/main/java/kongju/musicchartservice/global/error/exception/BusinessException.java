package kongju.musicchartservice.global.error.exception;

import kongju.musicchartservice.global.error.ErrorCode;

public class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
