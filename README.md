# EduHub

사내 교육과정 조회-수강 신청 시스템

## 개발용 로그인 (dev 프로필)

dev 프로필로 실행하면 개발용 계정 `E1001`·`E1002`(사원)와 `A0001`(관리자)이 만들어진다(이미 있으면 그대로 둔다). 세 계정의 비밀번호는 `.env`(커밋 금지)의 `EDUHUB_DEV_PASSWORD`로 정한다.

- 값 앞뒤에 공백을 넣지 않는다. UTF-8 72바이트 이하여야 하고, 비어 있거나 넘으면 계정을 만들지 않고 경고만 남긴다.
- 변수 이름은 `.env.example`에 있다. 비밀번호 값은 코드와 설정 파일에 쓰지 않는다.

확인용 curl (dev 프로필, 포트 8085)

```bash
# 로그인: 응답의 accessToken을 TOKEN에 담는다
curl -s -X POST localhost:8085/api/auth/login -H 'Content-Type: application/json' \
  -d '{"employeeNo":"E1001","password":"'"$EDUHUB_DEV_PASSWORD"'"}'
# 현재 사용자
curl -s localhost:8085/api/me -H "Authorization: Bearer $TOKEN"
# 사원 토큰으로 관리자 API 호출: 403
curl -s -o /dev/null -w '%{http_code}\n' -X POST localhost:8085/api/v1/admin/courses \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{}'
```
