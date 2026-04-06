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
     * 1. DB 제약 조건 위반 (중복 데이터, Null 위반 등)
     * 주로 사용자가 이미 있는 데이터를 또 넣으려 할 때 발생
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    protected ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolationException(DataIntegrityViolationException e) {
        log.error("데이터 제약 조건 위반 발생: ", e);
        return ResponseEntity
                .status(ErrorCode.DATA_INTEGRITY_VIOLATION.getStatus()) // 409 또는 400
                .body(ApiResponse.fail(ErrorCode.DATA_INTEGRITY_VIOLATION));
    }

    /**
     * 2. DB 연결 실패 및 자원 문제 (Redis 꺼짐, DB 서버 다운 등)
     * 시스템 본체의 하드웨어/네트워크 문제일 때 발생
     */
    @ExceptionHandler({
            DataAccessResourceFailureException.class,
            UncategorizedDataAccessException.class
    })
    protected ResponseEntity<ApiResponse<Void>> handleDatabaseResourceException(Exception e) {
        log.error("DB 자원/연결 오류 발생: ", e);
        return ResponseEntity
                .status(ErrorCode.DATABASE_CONNECTION_ERROR.getStatus()) // 500
                .body(ApiResponse.fail(ErrorCode.DATABASE_CONNECTION_ERROR));
    }

    /**
     * 3. 낙관적 락 충돌 (동시 수정 문제)
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    protected ResponseEntity<ApiResponse<Void>> handleOptimisticLockingException(OptimisticLockingFailureException e) {
        log.error("동시성 수정 충돌 발생: ", e);
        return ResponseEntity
                .status(ErrorCode.CONCURRENCY_ERROR.getStatus()) // 409
                .body(ApiResponse.fail(ErrorCode.CONCURRENCY_ERROR));
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
