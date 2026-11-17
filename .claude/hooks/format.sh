#!/usr/bin/env bash
# PostToolUse(Edit|Write): Claude가 수정한 파일을 포맷한다.
# 포맷 도구가 없거나 실패해도 작업을 멈추지 않도록 항상 exit 0으로 끝낸다.

input=$(cat)
command -v jq >/dev/null 2>&1 || exit 0
file_path=$(printf '%s' "$input" | jq -r '.tool_input.file_path // empty' 2>/dev/null)
[ -z "$file_path" ] && exit 0

root="${CLAUDE_PROJECT_DIR:-$(pwd)}"

case "$file_path" in
  *.java)
    if [ -f "$root/backend/gradlew" ]; then
      (cd "$root/backend" && ./gradlew spotlessApply -q) >/dev/null 2>&1
    fi
    ;;
  *.ts | *.tsx)
    prettier="$root/frontend/node_modules/.bin/prettier"
    if [ -f "$prettier" ]; then
      (cd "$root/frontend" && "$prettier" --write "$file_path") >/dev/null 2>&1
    fi
    ;;
esac

exit 0
