package kongju.musicchartservice.global.error;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.UncategorizedDataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import kongju.musicchartservice.global.common.ApiResponse;
import kongju.musicchartservice.global.error.exception.BusinessException;


@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    /**
     * 비지니스 예외
     * @param e
     * @return
     */
    @ExceptionHandler(BusinessException.class)
    protected ResponseEntity<ApiResponse<Void>> businessException(BusinessException e) {
        log.error(e.getMessage());
        ErrorCode errorCode = e.getErrorCode();
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiResponse.fail(errorCode));
    }

    /**
     * 예상치 못한 예외
     * @param e
     * @return
     */
    @ExceptionHandler(Exception.class)
    protected ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("Exception : ", e);
        return ResponseEntity
                .status(ErrorCode.INTERNAL_SERVER_ERROR.getStatus())
                .body(ApiResponse.fail(ErrorCode.INTERNAL_SERVER_ERROR));

    }

    /**
     * DB 연결 실패 등 자원 문제
     * @param e
     * @return
     */
    @ExceptionHandler({
            DataAccessResourceFailureException.class,
            UncategorizedDataAccessException.class,
            OptimisticLockingFailureException.class,
            DataIntegrityViolationException.class
    })
    protected ResponseEntity<ApiResponse<Void>> handleDatabaseErrorsException(DataAccessResourceFailureException e) {
        log.error("Exception : ", e);
        return ResponseEntity
                .status(ErrorCode.DATA_INTEGRITY_VIOLATION.getStatus())
                .body(ApiResponse.fail(ErrorCode.DATA_INTEGRITY_VIOLATION));

    }

    /**
     * 유효성 검사 실패
     * @param e
     * @return
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    protected ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().get(0).getDefaultMessage();
        log.error("Validation Failed: {}", message);
        return ResponseEntity
                .status(ErrorCode.INVALID_INPUT.getStatus())
                .body(ApiResponse.fail(message));
    }

}
