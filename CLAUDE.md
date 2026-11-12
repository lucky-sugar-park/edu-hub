# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 프로젝트

EduHub는 사내 교육과정 조회-수강 신청 시스템이다.
사용자는 임직원(수강생)과 교육 담당자(관리자)다.

## 디렉터리 지도

- `backend/`: Spring Boot API 서버. 규칙은 `backend/CLAUDE.md`에 있다.
- `frontend/`: React 화면(npm workspaces). 규칙은 `frontend/CLAUDE.md`에 있다.
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


## 개발 진행 절차 (기능 단위)
1. 시작: /fr-start <요구사항ID> → 설계명세(docs/design/) → 개발자 승인 → 구현계획(docs/plans/) → 개발자 승인
2. 구현: 계획의 작업마다 implementer에게 맡기고, 끝나면 reviewer로 검토한다
   - 심각·경고는 implementer에게 수정시킨다. 수정·재검토는 최대 2회, 넘으면 개발자에게 보고한다
3. 최종 검토: 모든 작업이 끝나면 critical-reviewer에게 설계명세 + 전체 diff + 1차 검토 결과를 넘긴다
4. 서브에이전트를 호출할 때 model은 지정하지 않는다 (정의 파일의 model을 따른다)
5. 3개 이상 파일·여러 계층에 걸친 작업이나 diff 1,000줄 이상은 개발자에게 알리고 지시를 받는다
6. 수량 변경(정원, 대기 순번)에는 반드시 락, 중복 방지는 DB 유일 제약으로 보장한다