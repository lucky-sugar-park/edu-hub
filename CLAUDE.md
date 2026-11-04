# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 프로젝트

EduHub는 사내 교육과정 조회-수강 신청 시스템이다.
사용자는 임직원(수강생)과 교육 담당자(관리자)다.

## 디렉터리 지도

- `backend/`: Spring Boot API 서버. 규칙은 `backend/CLAUDE.md`에 있다.
- `frontend/`: React 화면. 아직 코드가 없고, 규칙은 `frontend/CLAUDE.md`에 있다.
- `docs/requirements.md`: 요구사항의 기준. FR/NFR 표와 "결정 사항"(D-xx)이 함께 규칙을 이룬다. 주차별 범위는 FR 표의 "주차" 열을 따른다.

## 실행 명령

`backend/`에서 실행한다.

```bash
./gradlew test    # 테스트 (로컬 MariaDB 필요)
./gradlew bootRun # dev 프로필로 실행 (로컬 MariaDB 필요)
```

## 공통 규칙

- 커밋: Conventional Commits 형식(`feat:`, `docs:`, `chore:` 등)에 한국어 설명을 붙인다.
- 브랜치: `<type>/<설명>` 형식(예: `feature/week1-skeleton`)으로 만들고, `main`에는 PR로만 병합한다.
- 비밀 정보: 코드와 설정 파일에 쓰지 않는다. 환경 변수와 `.env`(커밋 금지)만 사용한다.
