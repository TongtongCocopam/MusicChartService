# Music Chart Service

멜론, 지니, VIBE의 음원 차트 정보를 수집하고 조회할 수 있는 API 서버입니다.

각 음원 사이트의 차트와 앨범 상세 정보를 수집하여 순위, 곡명, 아티스트, 앨범, 발매사, 기획사 정보를 제공합니다.

외부 사이트 요청이 많은 서비스 특성상 Spring WebFlux와 WebClient를 사용해 비동기 요청을 처리했고, 반복적인 스크래핑을 줄이기 위해 Redis 캐시를 적용했습니다.

또한 캐시 만료 시 같은 vendor에 대한 요청이 동시에 들어오는 상황을 고려해 Redis 기반 락을 적용하여 중복 스크래핑이 발생하지 않도록 구현했습니다.

---

## Tech Stack

- Java
- Spring Boot
- Spring WebFlux
- WebClient
- Spring Data JPA
- PostgreSQL
- Redis
- Jsoup
- Docker Compose
- Gradle

PostgreSQL과 Redis는 Docker Compose를 이용해 로컬 개발 환경을 구성했습니다.

---

## Features

- Melon / Genie / VIBE 음원 차트 수집
- 차트 순위, 곡명, 아티스트, 앨범 정보 조회
- 앨범 상세 페이지를 이용한 발매사 / 기획사 수집
- WebClient 기반 비동기 외부 요청
- Reactor `Mono`, `Flux`를 이용한 데이터 처리
- Redis 기반 30분 캐싱
- DB 데이터를 이용한 Redis 캐시 복구
- Redis Lock을 이용한 중복 스크래핑 방지
- JPA 기반 스크래핑 결과 저장
- 공통 API 응답 및 전역 예외 처리

---

## Scraper

멜론, 지니, VIBE는 사이트마다 페이지 구조와 응답 형태가 다르기 때문에 각각 별도의 스크래퍼를 구현했습니다.

서비스에서 사이트별 구현체를 직접 구분하지 않도록 `MusicScraper` 인터페이스를 사용했습니다.

```java
public interface MusicScraper {

    Mono<List<MusicScrapingContext>> scrape();

    Vendor getScraperName();
}
```

`MelonScraper`, `GenieScraper`, `VibeScraper`가 해당 인터페이스를 구현하고 있으며, 서비스에서는 Spring이 주입한 스크래퍼 목록을 `Vendor` 기준으로 매핑하여 사용합니다.

새로운 사이트가 추가될 경우 기존 서비스 로직에 조건문을 추가하기보다 새로운 `MusicScraper` 구현체를 추가하는 방식으로 확장할 수 있도록 구성했습니다.

---

## WebFlux를 이용한 외부 요청

차트 정보만 가져오는 것이 아니라 각 곡의 앨범 상세 페이지까지 추가로 요청해야 하기 때문에 한 번의 스크래핑에서 많은 외부 HTTP 요청이 발생합니다.

Spring WebFlux의 `WebClient`를 사용하고, 여러 요청은 `Flux`와 `flatMap()`을 이용해 처리했습니다.

예를 들어 지니 차트는 Top 200이 여러 페이지로 나뉘어 있기 때문에 각 페이지를 비동기로 요청한 뒤 하나의 목록으로 합칩니다.

```java
Flux.range(1, 4)
    .flatMap(pg -> ...)
    .flatMapIterable(list -> list)
    .collectList();
```

이후 수집한 곡의 앨범 상세 페이지도 다시 비동기로 조회하여 발매사와 기획사 정보를 추가합니다.

외부 사이트의 응답 지연에 대비해 WebClient에는 Connection / Response / Read / Write Timeout도 설정했습니다.

---

## Blocking 작업 처리

WebFlux를 사용하더라도 Jsoup HTML 파싱이나 JPA 접근은 blocking 작업이기 때문에 Netty Event Loop에서 직접 실행하지 않도록 했습니다.

해당 작업은 `Mono.fromCallable()`로 감싼 뒤 `boundedElastic` Scheduler에서 처리했습니다.

```java
Mono.fromCallable(() -> {
    // Jsoup Parsing or JPA
})
.subscribeOn(Schedulers.boundedElastic());
```

외부 HTTP 요청과 Redis 접근은 Reactive 방식으로 처리하고, JPA와 Jsoup처럼 blocking이 필요한 작업만 별도의 Scheduler에서 실행하도록 분리했습니다.

---

## Redis Cache

스크래핑 결과를 조회할 때 Redis를 먼저 확인합니다.

차트 요약, 전체 상세 목록, 개별 곡 정보를 각각 다른 키로 저장하고 TTL은 30분으로 설정했습니다.

```text
summary:{vendor}
detail:{vendor}
song:{songId}
```

Redis에 데이터가 있으면 DB나 외부 음원 사이트에 접근하지 않고 바로 반환합니다.

Redis에 캐시가 없는 경우에는 DB에 최근 30분 이내 수집된 데이터가 남아 있는지 확인합니다.

DB 데이터가 아직 유효하다면 새로 스크래핑하지 않고 해당 데이터를 이용해 Redis 캐시를 다시 구성하고, DB 데이터까지 오래된 경우에만 새로운 스크래핑을 진행합니다.

---

## Redis Lock

캐시와 DB 데이터가 모두 오래된 시점에 같은 vendor 요청이 동시에 들어오면 여러 요청이 동시에 스크래핑을 시작할 수 있습니다.

이를 막기 위해 Redis의 `setIfAbsent()`를 이용해 vendor별 락을 구현했습니다.

```java
redisTemplate.opsForValue()
    .setIfAbsent(lockKey, "LOCKED", Duration.ofSeconds(10));
```

락을 획득한 요청만 스크래핑을 수행하며, 락을 얻은 뒤에도 캐시를 한 번 더 확인합니다.

락을 기다리는 동안 다른 요청이 먼저 데이터를 갱신했을 수 있기 때문에, 이미 캐시가 생성되어 있다면 다시 스크래핑하지 않습니다.

락을 획득하지 못한 요청은 바로 실패시키지 않고 500ms 간격으로 재시도하도록 했습니다.

```java
Retry.fixedDelay(10, Duration.ofMillis(500))
```

락 키에는 10초 TTL을 설정하여 작업 중 오류가 발생해도 락이 계속 남지 않도록 했습니다.

---

## Database

스크래핑 결과는 `MusicSummary`와 `MusicDetail`로 나누어 저장했습니다.

차트에서 자주 조회되는 정보와 발매사 / 기획사 정보를 분리하고, 동일한 상세 정보는 여러 차트 데이터가 함께 참조할 수 있도록 구성했습니다.

### MusicSummary

| Column | Type | Description |
|---|---|---|
| `id` | Long | PK |
| `ranking` | int | 차트 순위 |
| `title` | String | 곡명 |
| `artist` | String | 아티스트 |
| `album` | String | 앨범명 |
| `songId` | String | 음원 사이트의 곡 ID |
| `vendor` | Enum | MELON / GENIE / VIBE |
| `musicDetail` | MusicDetail | 상세정보 |
| `createdAt` | LocalDateTime | 데이터 수집 시각 |

### MusicDetail

| Column | Type | Description |
|---|---|---|
| `id` | Long | PK |
| `publisher` | String | 발매사 |
| `agency` | String | 기획사 |

`MusicSummary`는 `MusicDetail`을 `ManyToOne`으로 참조합니다.

스크래핑 데이터를 저장할 때 동일한 발매사와 기획사 조합은 하나의 `MusicDetail`을 사용하도록 처리했습니다.

---

## API

### 차트 요약 조회

```http
GET /api/v1/music-chart/{vendor}/summary
```

차트 순위, 곡명, 아티스트 정보를 반환합니다.

```http
GET /api/v1/music-chart/melon/summary
```

### 전체 음원 조회

```http
GET /api/v1/music-chart/{vendor}/songs
```

차트 정보와 앨범 상세정보를 함께 반환합니다.

### 단일 음원 조회

```http
GET /api/v1/music-chart/{vendor}/song/{music_id}
```

특정 음원의 상세 정보를 반환합니다.

---

## API Response

API 응답은 `ApiResponse<T>`로 통일했습니다.

성공:

```json
{
  "success": true,
  "data": {},
  "message": null
}
```

실패:

```json
{
  "success": false,
  "data": null,
  "message": "에러 메시지"
}
```

---

## Exception Handling

비즈니스 예외는 `BusinessException`과 `ErrorCode`를 이용해 관리하고 `@RestControllerAdvice`에서 공통 처리합니다.

| 상황 | Status |
|---|---|
| 존재하지 않는 Vendor | 404 Not Found |
| 존재하지 않는 Music ID | 404 Not Found |
| 잘못된 요청 | 400 Bad Request |
| 데이터 제약조건 위반 | 422 Unprocessable Entity |
| 동시성 충돌 | 409 Conflict |
| DB 연결 오류 | 500 Internal Server Error |
| 외부 사이트 스크래핑 실패 | 502 Bad Gateway |
| 일시적인 서비스 장애 | 503 Service Unavailable |

예상하지 못한 서버 오류도 동일한 API 응답 형식으로 반환하도록 구성했습니다.

---

## Docker

PostgreSQL과 Redis는 Docker Compose를 이용해 실행합니다.

```bash
docker compose up -d
```

Spring Boot 실행:

```bash
./gradlew bootRun
```

Windows:

```bash
gradlew.bat bootRun
```

---

## What I Focused On

이 프로젝트에서 가장 많이 고려한 부분은 외부 사이트 요청을 어떻게 줄이고, 동시에 들어오는 요청에서 불필요한 스크래핑이 반복되지 않도록 할지였습니다.

차트 하나를 구성하기 위해 여러 외부 페이지를 요청해야 하기 때문에 WebClient와 Reactor를 이용해 비동기로 처리했고, 반복 요청은 Redis 캐시를 통해 줄였습니다.

Redis 캐시가 사라진 경우에도 바로 외부 사이트를 다시 요청하지 않고 DB의 최근 데이터를 확인해 캐시를 복구하도록 했습니다.

또한 캐시 만료 시 같은 vendor 요청이 동시에 들어오는 상황에서는 Redis Lock을 사용해 실제 스크래핑은 하나의 요청만 수행하도록 했습니다.

WebFlux를 사용하면서도 JPA와 Jsoup처럼 blocking 방식으로 동작하는 부분은 `boundedElastic`로 분리하여 Event Loop가 blocking되지 않도록 처리했습니다.