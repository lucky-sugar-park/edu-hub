# backend

## 테스트 명령

`backend/`에서 실행한다.

```bash
./gradlew test                                  # 전체 테스트 (로컬 MariaDB 필요)
./gradlew test --tests "*HealthControllerTest"  # 테스트 하나만 실행
./gradlew spotlessApply                         # 포맷 적용
./gradlew check                                 # 테스트 + 포맷 검사
```

`test`에는 포맷 검사가 포함되지 않으므로, 작업을 마칠 때는 `check`를 실행한다.

## 계층 규칙

- Controller → Service → Repository 순서로 호출한다. Controller에서 Repository를 직접 호출하지 않는다.
- 패키지는 `com.example.eduhub` 아래 도메인별로 나눈다. `course`는 과정과 회차, `enrollment`는 신청·대기·취소·승급·수료, `common`은 공통 코드를 담는다(NFR-07).

## JPA 규칙

- 연관관계는 LAZY로 둔다.
- 엔티티를 API 응답으로 그대로 반환하지 않고 DTO를 반환한다.
- `open-in-view=false`이므로 지연 로딩은 트랜잭션 안에서 끝내야 한다(NFR-02).
- 스키마는 Flyway로만 변경한다. `ddl-auto: validate`이므로 엔티티를 추가할 때 마이그레이션도 함께 추가해야 한다.

## OpenAPI 규칙

- 모든 API는 OpenAPI 문서가 있어야 하고, 프론트 클라이언트는 이 스펙에서 생성한다(NFR-04).
- 문서는 springdoc 기본 경로인 `/v3/api-docs`와 `/swagger-ui.html`에서 제공된다.
- 에러 응답은 `common/ErrorResponse`(`code`, `message`) 형식이며, `common/GlobalExceptionHandler`에서 처리한다.

## 프로필과 환경 변수

- `dev`: 로컬 MariaDB의 `eduhub` 스키마에 접속한다. `DB_USERNAME`, `DB_PASSWORD`는 환경 변수 또는 `.env`(`backend/`나 저장소 루트)로 지정하며, 이름은 `.env.example`에 있다.
- `test`: 같은 계정으로 로컬 MariaDB의 `eduhub_test` 스키마에 접속한다. DB가 필요한 테스트는 `@ActiveProfiles("test")`를 붙이고, `@DataJpaTest`에는 `@AutoConfigureTestDatabase(replace = NONE)`도 붙인다.
- `eduhub`와 `eduhub_test` 스키마는 미리 만들어 두어야 한다.

## 구현 시 유의할 점

- 동시성 테스트(NFR-01)는 락과 인덱스 동작을 확인해야 하므로 H2가 아닌 실제 MariaDB에서 돌린다.
- 취소 후 재신청이 가능하므로(D-05), 중복 신청 방지에 `회차+사용자` 일반 유니크 제약을 쓸 수 없다. MariaDB에는 부분 인덱스도 없으므로 다른 방법이 필요하다.
- 정원 감소 시 대기로 바뀐 사람은 기존 대기자보다 앞 순번이 된다(D-03). 대기 순서를 설계할 때 이를 고려한다.
- 락 방식과 대기 순서 표현 방식은 아직 정해지지 않았다. 구현 전에 `docs/requirements.md`의 "결정 사항"에 기록한다.

## 빌드 환경 주의

- JDK는 Gradle 툴체인으로 Java 21에 고정되어 있고, 없으면 foojay로 내려받는다.
- 사내망의 TLS 검사 때문에 Gradle 다운로드가 "could not resolve plugin artifact"로만 실패할 수 있다. JDK가 사내 루트 인증서를 신뢰하지 않는 것이 원인이다. `~/.gradle/gradle.properties`에 `systemProp.javax.net.ssl.trustStoreType=Windows-ROOT`를 넣으면 해결된다. 전역 `org.gradle.jvmargs`는 프로젝트별 값을 덮어쓰므로 쓰지 않는다.
- Spotless의 `googleJavaFormat("1.37.0")` 버전 지정은 실패했다. 지금은 버전을 지정하지 않고 기본값을 쓴다.
