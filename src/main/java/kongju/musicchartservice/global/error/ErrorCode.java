package kongju.musicchartservice.global.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    NOT_FOUND_VENDOR(HttpStatus.NOT_FOUND, "일치하는 VENDOR가 없습니다"),
    NOT_FOUND_MUSIC_ID(HttpStatus.NOT_FOUND, "일치하는 MUSIC_ID가 없습니다"),

    INVALID_INPUT(HttpStatus.BAD_REQUEST, "잘못된 요청입니다"),

    DUPLICATE_RESOURCE(HttpStatus.CONFLICT, "이미 존재하는 데이터입니다"),
    DATA_INTEGRITY_VIOLATION(HttpStatus.UNPROCESSABLE_ENTITY, "데이터 제약 조건을 위반했습니다"),

    // DB 서버나 Redis 서버 자체에 문제
    DATABASE_CONNECTION_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "데이터베이스 연결에 실패했습니다"),

    // 락 충돌이나 동시성 문제
    CONCURRENCY_ERROR(HttpStatus.CONFLICT, "동시에 동일한 데이터를 수정하여 충돌이 발생했습니다"),

    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 에러가 발생했습니다"),

    SCRAPING_FAILED(HttpStatus.BAD_GATEWAY, "외부 사이트 스크래핑에 실패했습니다"),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "일시적으로 서비스 불가합니다"),

    SCRAPING_LOCKED(HttpStatus.CONFLICT , "스크래핑이 이미 실행 중 입니다");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
