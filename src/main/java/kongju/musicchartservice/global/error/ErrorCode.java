package kongju.musicchartservice.global.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    NOT_FOUND_VENDOR(HttpStatus.BAD_REQUEST, "일치하는 VENDOR가 없습니다"),
    NOT_FOUND_MUSIC_ID(HttpStatus.BAD_REQUEST, "일치하는 MUSIC_ID가 없습니다"),

    INVALID_INPUT(HttpStatus.BAD_REQUEST, "잘못된 입력값입니다"),

    DUPLICATE_RESOURCE(HttpStatus.CONFLICT, "이미 존재하는 데이터입니다"),
    DATA_INTEGRITY_VIOLATION(HttpStatus.UNPROCESSABLE_ENTITY, "데이터 타입이 불일치하거나 제약 조건을 위반했습니다"),

    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 에러가 발생했습니다"),
    DATABASE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "데이터베이스 처리 중 에러가 발생했습니다"),
    DATA_ACCESS_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "DB 접근 중 알 수 없는 오류가 발생했습니다"),

    SCRAPING_FAILED(HttpStatus.BAD_GATEWAY, "외부 사이트 스크래핑에 실패했습니다"),
    EXTERNAL_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "외부 서비스가 일시적으로 응답하지 않습니다"),
    SCRAPING_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "스크래핑 시도 중 시간 초과가 발생했습니다");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
