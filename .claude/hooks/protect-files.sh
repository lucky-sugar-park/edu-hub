#!/usr/bin/env bash
# PreToolUse(Edit|Write): 사람이 관리하는 파일의 수정을 막는다.
# 막을 때는 stderr에 이유와 대신 할 일을 쓰고 exit 2로 끝낸다.

input=$(cat)
if ! command -v jq >/dev/null 2>&1; then
  echo "protect-files: jq가 없어 파일 보호 검사를 건너뜀" >&2
  exit 1
fi
file_path=$(printf '%s' "$input" | jq -r '.tool_input.file_path // empty' 2>/dev/null)
[ -z "$file_path" ] && exit 0

# Windows 경로(D:\...)도 같은 규칙으로 보도록 / 로 바꾸고 소문자로 비교한다.
backslash=$(printf '\x5c')
path=${file_path//"$backslash"//}
path=${path,,}
name=${path##*/}

block() {
  echo "$1" >&2
  exit 2
}

case "$name" in
  application-prod.yml)
    block "운영 설정은 사람이 PR로만 수정한다. 개발 설정은 application-dev.yml을 수정할 것." ;;
  .env.example) ;;
  .env | .env.*)
    block "비밀 파일은 수정하지 않는다. 필요한 변수는 .env.example에 이름만 추가할 것." ;;
esac

case "$path" in
  */db/migration/v*.sql)
    if [ -e "$file_path" ]; then
      block "기존 마이그레이션은 수정 금지. 새 버전 파일(V다음번호__설명.sql)을 추가할 것."
    fi ;;
  */frontend/packages/api-client/src/generated/*)
    block "생성 코드는 직접 수정하지 않는다. 백엔드 스펙을 바꾼 뒤 frontend/에서 npm run generate:api를 실행할 것." ;;
esac

exit 0
