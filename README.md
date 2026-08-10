# Music Chart Service

멜론, 지니, VIBE의 음원 차트 정보를 수집하고 조회할 수 있는 API 서버입니다.

인턴십에서 FastAPI로 구현했던 음원 차트 스크래핑 과제를 이후 Spring을 공부하면서 다시 구현했습니다. 단순히 기존 코드를 Java로 옮기기보다는 Spring WebFlux를 이용한 비동기 외부 요청, Redis 캐싱과 동시 요청 처리 등을 추가하면서 구조를 다시 설계했습니다.

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

로컬 개발 환경에서는 Docker Compose를 이용해 PostgreSQL과 Redis를 실행했습니다.

---

## Features

- Melon / Genie / VIBE 음원 차트 수집
- 차트 순위, 곡명, 가수, 앨범 정보 조회
- 앨범 상세 페이지를 이용한 발매사 / 기획사 수집
- WebClient 기반 비동기 외부 요청
- Reactor `Mono`, `Flux`를 이용한 데이터 처리
- Redis 30분 캐싱
- DB 데이터 기반 Redis 캐시 복구
- Redis 기반 스크래핑 중복 실행 방지
- JPA를 이용한 스크래핑 결과 저장
- 공통 API 응답 형식 및 전역 예외 처리

---

## Scraper

멜론, 지니, VIBE는 각각 페이지 구조와 응답 형태가 달라 사이트별 스크래퍼를 따로 구현했습니다.

각 스크래퍼가 동일한 방식으로 사용될 수 있도록 `MusicScraper` 인터페이스를 만들었습니다.

```java
public interface MusicScraper {

    Mono<List<MusicScrapingContext>> scrape();

    Vendor getScraperName();
}
```

`MelonScraper`, `GenieScraper`, `VibeScraper`가 해당 인터페이스를 구현하고 있으며, 서비스에서는 Spring이 주입한 스크래퍼 목록을 `Vendor` 기준으로 매핑해 사용합니다.

새로운 음원 사이트를 추가할 때도 기존 서비스 로직에 조건문을 추가하기보다는 새로운 `MusicScraper` 구현체를 추가하는 방식으로 확장할 수 있도록 구성했습니다.

---

## WebFlux를 이용한 외부 요청

음원 차트 한 페이지뿐만 아니라 각 곡의 앨범 상세 페이지까지 추가로 요청해야 하기 때문에 외부 HTTP 요청이 많이 발생합니다.

Spring 버전에서는 `WebClient`를 사용하고, 여러 요청은 `Flux`와 `flatMap()`을 이용해 처리했습니다.

예를 들어 지니 차트는 Top 200이 여러 페이지로 나뉘어 있어 `Flux.range()`를 이용해 각 페이지를 요청하고, 가져온 음원의 앨범 정보도 다시 비동기로 조회합니다.

```java
Flux.range(1, 4)
    .flatMap(pg -> ...)
    .flatMapIterable(list -> list)
    .collectList();
```

WebClient에는 외부 사이트의 응답 지연을 고려하여 Connection / Response / Read / Write Timeout도 설정했습니다.

---

## Blocking 작업 처리

WebFlux를 사용하더라도 Jsoup HTML 파싱이나 JPA 같은 작업은 blocking 방식이기 때문에 Netty Event Loop에서 그대로 처리하지 않도록 했습니다.

이런 작업은 `Mono.fromCallable()`로 감싼 뒤 `boundedElastic` Scheduler에서 실행하도록 구성했습니다.

```java
Mono.fromCallable(() -> {
    // Jsoup Parsing or JPA
})
.subscribeOn(Schedulers.boundedElastic());
```

HTTP 요청과 Redis 접근은 Reactive 방식으로 처리하면서, JPA와 Jsoup처럼 blocking이 필요한 부분만 별도의 Scheduler로 분리했습니다.

---

## Redis Cache

스크래핑 결과를 요청할 때 Redis를 먼저 확인합니다.

차트 요약, 상세 목록, 개별 곡을 각각 다른 키로 저장하고 TTL은 30분으로 설정했습니다.

```text
summary:{vendor}
detail:{vendor}
song:{songId}
```

Redis에 데이터가 있다면 DB나 외부 음원 사이트까지 접근하지 않고 바로 반환합니다.

Redis에 캐시가 없으면 DB의 최근 데이터도 확인합니다. 30분 이내에 수집된 데이터가 남아 있다면 다시 스크래핑하지 않고 DB 데이터를 이용해 Redis 캐시를 다시 구성합니다.

DB 데이터까지 오래된 경우에만 새로운 스크래핑을 진행합니다.

---

## Redis Lock

캐시와 DB 데이터가 모두 오래된 상태에서 같은 vendor 요청이 여러 개 들어오면 스크래핑이 동시에 여러 번 실행될 수 있어서 Redis 락을 추가했습니다.

`setIfAbsent()`를 이용해서 `lock:{vendor}` 키를 생성하고, 락을 획득한 요청만 스크래핑을 수행하도록 했습니다.

```java
redisTemplate.opsForValue()
    .setIfAbsent(lockKey, "LOCKED", Duration.ofSeconds(10));
```

락을 얻은 이후에도 캐시를 한 번 더 확인합니다. 락을 기다리는 사이 다른 요청에서 이미 데이터를 갱신했을 수 있기 때문에, 캐시가 생겼다면 스크래핑을 다시 하지 않습니다.

락을 획득하지 못한 요청은 바로 실패시키지 않고 500ms 간격으로 재시도하도록 했습니다.

```java
Retry.fixedDelay(10, Duration.ofMillis(500))
```

락 키에는 10초 TTL을 두어 작업 중 문제가 발생해도 락이 계속 남지 않도록 했습니다.

---

## Database

스크래핑 결과는 `MusicSummary`와 `MusicDetail`로 나누어 저장했습니다.

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

`MusicSummary`가 `MusicDetail`을 `ManyToOne`으로 참조합니다.

스크래핑 결과를 저장할 때 동일한 발매사와 기획사 조합은 하나의 `MusicDetail`을 사용하도록 처리했습니다.

---

## API

### 차트 요약 조회

```http
GET /api/v1/music-chart/{vendor}/summary
```

순위, 곡명, 아티스트 등의 차트 정보를 반환합니다.

예시:

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

API 응답 형식을 `ApiResponse<T>`로 통일했습니다.

성공 응답은 다음과 같은 형태입니다.

```json
{
  "success": true,
  "data": {},
  "message": null
}
```

실패 응답은 다음과 같습니다.

```json
{
  "success": false,
  "data": null,
  "message": "에러 메시지"
}
```

---

## Exception Handling

비즈니스 예외는 `BusinessException`과 `ErrorCode`를 이용해 관리하고, `@RestControllerAdvice`에서 공통으로 처리했습니다.

주요 상태 코드는 다음과 같습니다.

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

예상하지 못한 서버 오류도 전역 예외 처리에서 공통 응답 형태로 반환하도록 구성했습니다.

---

## Docker

PostgreSQL과 Redis는 Docker Compose를 이용해 로컬 개발 환경을 구성했습니다.

```bash
docker compose up -d
```

실행 중인 컨테이너는 다음 명령으로 확인할 수 있습니다.

```bash
docker compose ps
```

종료:

```bash
docker compose down
```

---

## Run

Docker로 PostgreSQL과 Redis를 먼저 실행합니다.

```bash
docker compose up -d
```

이후 Spring Boot 애플리케이션을 실행합니다.

```bash
./gradlew bootRun
```

Windows에서는:

```bash
gradlew.bat bootRun
```

---

## FastAPI 버전과의 차이

이 프로젝트는 인턴십에서 구현했던 FastAPI 버전을 Spring 환경에서 다시 구현한 프로젝트입니다.

FastAPI에서는 `httpx`, `asyncio.gather()`를 이용해 비동기 스크래핑을 처리했고 SQLite File DB를 캐시처럼 사용했습니다.

Spring 버전에서는 `WebClient`, `Mono`, `Flux`를 이용해 외부 요청을 처리하고 Redis를 별도의 캐시 계층으로 추가했습니다. 동시에 같은 사이트의 스크래핑이 실행되는 문제도 기존 `asyncio.Lock` 대신 Redis 기반 락을 사용하는 방식으로 변경했습니다.

이를 통해 같은 요구사항을 Python과 Spring 두 환경에서 구현하면서 비동기 I/O와 캐싱, 동시 요청 처리 방법의 차이를 비교해볼 수 있었습니다.