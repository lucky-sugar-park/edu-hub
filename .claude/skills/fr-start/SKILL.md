---
name: fr-start
description: EduHub 요구사항 하나를 설계명세와 구현계획부터 시작한다
argument-hint: "[요구사항ID 예: FR-05]"
disable-model-invocation: true
---

docs/requirements.md에서 $ARGUMENTS 를 찾아 다음 순서로 진행한다. 코드는 수정하지 않는다.

1. 요구사항과 관련 비기능 요구사항(NFR)을 정리하고, 애매한 점을 질문 목록으로 만든다
2. 설계명세를 docs/design/ 아래에 작성한다: API(메서드, 경로, 요청·응답, 오류 코드), 엔티티·스키마 변경, 트랜잭션·동시성 처리, 테스트 시나리오
3. 개발자의 승인을 받은 뒤, 구현계획을 docs/plans/ 아래에 작성한다: 작업 단위(각 작업의 변경 파일, 메서드, 테스트, 완료 명령), 작업 순서, 위험 영역
4. 각 작업이 implementer(구현)·reviewer(검토)로 진행될 수 있을 만큼 구체적으로 쓴다