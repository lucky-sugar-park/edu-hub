# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 프로젝트

EduHub는 사내 교육과정 조회-수강 신청 시스템이다. 사용자는 임직원(수강생)과 교육 담당자(관리자)다.

요구사항의 기준은 `docs/requirements.md`다. FR/NFR 표와 "결정 사항"(D-01~)이 함께 규칙을 이루며, FR의 문구만으로는 부족하다. 예를 들어 FR-06의 자동 승급에는 D-01, D-02, D-07의 조건이 붙는다. 주차별 범위는 FR 표의 "주차" 열을 따른다.

## 현재 상태

- `backend/`: 뼈대만 있다. 실제 코드는 `GET /api/health`뿐이고, 엔티티와 Flyway 마이그레이션은 아직 없다.
- `frontend/`: 비어 있는 로컬 폴더이며 Git에 추적되지 않는다. React 18 + TypeScript, Module Federation(shell, course-mfe, enrollment-mfe), MUI, npm workspaces로 만들 예정이다.
- 결정됐지만 아직 구현되지 않은 것: Spring Security·JWT(D-11), GitHub Actions CI(D-13), Testcontainers, 아키텍처 테스트, `application-prod.yml`.

## 명령어

모두 `backend/`에서 실행한다.

```bash
./gradlew test                                  # 전체 테스트 (DB 불필요)
./gradlew test --tests "*HealthControllerTest"  # 테스트 하나만 실행
./gradlew spotlessApply                         # 포맷 적용
./gradlew check                                 # 테스트 + 포맷 검사
./gradlew bootRun                               # dev 프로필로 실행 (PostgreSQL 필요)
```

`test`에는 포맷 검사가 포함되지 않으므로, 작업을 마칠 때는 `check`를 실행한다.

API 문서는 springdoc 기본 경로인 `/v3/api-docs`와 `/swagger-ui.html`에서 제공된다.

## 백엔드 구조

- 패키지는 `com.example.eduhub` 아래 도메인별로 나눈다. `course`는 과정과 회차, `enrollment`는 신청·대기·취소·승급·수료, `common`은 공통 코드를 담는다(NFR-07).
- 스키마는 Flyway로만 관리한다. `ddl-auto: validate`이므로 엔티티를 추가할 때 마이그레이션도 함께 추가해야 한다.
- 에러 응답은 ProblemDetail 형식이고, `open-in-view`는 꺼져 있다. 지연 로딩은 트랜잭션 안에서 끝내야 한다(NFR-02).

## 프로필과 환경 변수

- `dev`: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`를 읽는다. 환경 변수 또는 `.env`(`backend/`나 저장소 루트)로 지정하며, 이름은 `.env.example`에 있다.
- `test`: DataSource, JPA, Flyway 자동 구성을 제외해 DB 없이 실행한다. 그래서 테스트 클래스에 `@ActiveProfiles("test")`를 붙여야 한다. 엔티티와 리포지토리를 추가하면 이 방식으로는 부족하므로 테스트 DB 구성을 함께 정해야 한다.

## 구현 시 유의할 점

- 동시성 테스트(NFR-01)는 락과 인덱스 동작을 확인해야 하므로 H2가 아닌 실제 PostgreSQL에서 돌린다.
- 취소 후 재신청이 가능하므로(D-05), 중복 신청 방지에 `회차+사용자` 일반 유니크 제약을 쓸 수 없다.
- 정원 감소 시 대기로 바뀐 사람은 기존 대기자보다 앞 순번이 된다(D-03). 대기 순서를 설계할 때 이를 고려한다.
- 락 방식과 대기 순서 표현 방식은 아직 정해지지 않았다. 구현 전에 "결정 사항"에 기록한다.

## 빌드 환경 주의

- JDK는 Gradle 툴체인으로 Java 21에 고정되어 있고, 없으면 foojay로 내려받는다.
- 사내망의 TLS 검사(Somansa Root CA) 때문에 Gradle 다운로드가 "could not resolve plugin artifact"로만 실패할 수 있다. JDK가 이 인증서를 신뢰하지 않는 것이 원인이다. `~/.gradle/gradle.properties`에 `systemProp.javax.net.ssl.trustStoreType=Windows-ROOT`를 넣으면 해결된다. 전역 `org.gradle.jvmargs`는 프로젝트별 값을 덮어쓰므로 쓰지 않는다.
- Spotless의 `googleJavaFormat("1.37.0")` 버전 지정은 실패했다. 지금은 버전을 지정하지 않고 기본값을 쓴다.

## 커밋

메시지는 `feat:`, `docs:`, `chore:` 접두어에 한국어 설명을 붙인다.

## 팀 규칙
- 요구사항은 docs/requirements.md를 기준으로 한다 (결정 사항 D-xx 포함)
- 백엔드 빌드·테스트: cd backend && ./gradlew test
- 계층 규칙: Controller → Service → Repository. Controller에서 Repository 직접 호출 금지
- 엔티티를 API 응답으로 그대로 반환하지 않는다 (DTO 사용)
- 비밀 정보는 코드와 설정 파일에 쓰지 않는다. 환경 변수와 .env(커밋 금지)만 사용
