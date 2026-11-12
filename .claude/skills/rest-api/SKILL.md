\---

name: eduhub-api

description: EduHub 백엔드에 REST API를 추가·수정할 때 사용. "API 만들어줘", "엔드포인트 추가" 요청에 사용

disable-model-invocation: true

user-invocable: true

\---



\# 절차 ( 본문 )

1. DTO 
2. Service
3. Controller
4. ./gradlew test 통과 확인



\# 규칙

1. DTO는 record, 엔티티 반환 금지, 409는 DuplicateEnrollmentException



