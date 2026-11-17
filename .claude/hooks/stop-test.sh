#!/usr/bin/env bash
# Stop: 응답을 마칠 때 backend/src가 바뀌었으면 테스트를 돌리고, 실패하면 멈추지 않고 고치게 한다.
# 실패 시 {"decision":"block","reason":"..."}를 stdout으로 내보낸다. 그 밖에는 조용히 exit 0.

input=$(cat)
command -v jq >/dev/null 2>&1 || exit 0

# 이미 이 훅 때문에 계속 진행 중이면 다시 막지 않는다(무한 반복 방지).
if [ "$(printf '%s' "$input" | jq -r '.stop_hook_active // false' 2>/dev/null)" = "true" ]; then
  exit 0
fi

root="${CLAUDE_PROJECT_DIR:-$(pwd)}"
cd "$root" || exit 0

# backend/src에 변경(추적·미추적 모두)이 없으면 테스트하지 않는다.
[ -z "$(git status --porcelain -- backend/src 2>/dev/null)" ] && exit 0
[ -f backend/gradlew ] || exit 0

marker=$(mktemp)
output=$(cd backend && ./gradlew test -q 2>&1)
status=$?
if [ "$status" -eq 0 ]; then
  rm -f "$marker"
  exit 0
fi

# -q는 실패한 테스트 이름을 출력하지 않으므로, 이번 실행에서 생긴 결과 XML에서 실패를 꺼낸다.
xml_failures=$(find backend/build/test-results/test -name 'TEST-*.xml' -newer "$marker" 2>/dev/null |
  xargs -r grep -h -B1 '<failure message=' 2>/dev/null |
  sed -n -e 's/.*<testcase name="\([^"]*\)" classname="\([^"]*\)".*/\2 > \1 FAILED/p' \
    -e 's/.*<failure message="\([^"]*\)".*/    \1/p' |
  sed -e 's/&#13;//g' -e 's/&#10;/ /g' \
    -e 's/&quot;/"/g' -e 's/&lt;/</g' -e 's/&gt;/>/g' -e "s/&apos;/'/g" -e 's/&amp;/\&/g')
rm -f "$marker"

failures=$(printf '%s\n%s\n' "$xml_failures" "$(printf '%s\n' "$output" | grep -E 'FAILED|Exception')" |
  grep -v '^[[:space:]]*$' | head -n 20)
# 컴파일 오류처럼 위 패턴이 없는 실패는 마지막 20줄을 보여 준다.
[ -z "$failures" ] && failures=$(printf '%s\n' "$output" | tail -n 20)

reason="backend 테스트가 실패했다(./gradlew test). 원인을 찾아 고친 뒤 마칠 것. 테스트에 로컬 MariaDB가 필요하다.
${failures}"

jq -nc --arg reason "$reason" '{decision: "block", reason: $reason}'
exit 0
