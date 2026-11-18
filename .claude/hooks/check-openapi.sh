#!/usr/bin/env bash
# PostToolUse(Edit|Write): *Controller.java를 고친 뒤, 요청 매핑 메서드마다 @Operation이 있는지 검사한다.
# 매핑(@GetMapping 등)과 @Operation 수가 다르면 어느 메서드가 어긋났는지 stderr에 쓰고 exit 2로 Claude에게 알린다.
# 검사할 수 없는 경우(jq 없음, 파일 없음, 컨트롤러 아님)에는 막지 않고 exit 0.
# @Hidden이 붙은 클래스나 메서드는 OpenAPI 문서에서 빠지므로 검사하지 않는다.

input=$(cat)
command -v jq >/dev/null 2>&1 || exit 0
file_path=$(printf '%s' "$input" | jq -r '.tool_input.file_path // empty' 2>/dev/null)
[ -z "$file_path" ] && exit 0

# Windows 경로(C:\...\FooController.java)도 같은 규칙으로 보도록 구분자를 / 로 바꾼다.
backslash=$(printf '\x5c')
path=${file_path//"$backslash"//}
case "${path##*/}" in
  *Controller.java) ;;
  *) exit 0 ;;
esac
[ -f "$file_path" ] || exit 0

# 클래스 선언 전의 어노테이션은 클래스 것(@RequestMapping 접두 등), 메서드 선언 줄 전의 어노테이션은 그 메서드 것으로 본다.
# 출력: "missing <메서드> <줄>"(매핑은 있는데 @Operation 없음) 또는 "orphan <메서드> <줄>"(@Operation만 있고 매핑 없음)
problems=$(awk '
  function reset() { mapping = 0; operation = 0; hidden = 0 }
  BEGIN { reset(); classHidden = 0 }
  {
    line = $0
    sub(/\/\/.*$/, "", line)
    if (line ~ /^[[:space:]]*\*/ || line ~ /^[[:space:]]*\/\*/) next
    if (line ~ /@(Get|Post|Put|Delete|Patch|Request)Mapping([[:space:](]|$)/) mapping = 1
    if (line ~ /@Operation([[:space:](]|$)/) operation = 1
    if (line ~ /@Hidden([[:space:](]|$)/) hidden = 1
    if (line ~ /(^|[[:space:]])(class|interface|record)[[:space:]]+[A-Za-z_]/) {
      if (hidden) classHidden = 1
      reset(); next
    }
    if (line !~ /^[[:space:]]*@/ && line !~ /=/ && line !~ /(^|[[:space:]])(return|new|throw)[[:space:]]/ &&
        line ~ /^[[:space:]]*((public|protected|private)[[:space:]]+)?(static[[:space:]]+)?[A-Za-z_][A-Za-z0-9_<>,?.[:space:]\[\]]*[[:space:]]+[a-z][A-Za-z0-9_]*[[:space:]]*\(/) {
      name = line
      sub(/[[:space:]]*\(.*/, "", name)
      sub(/.*[[:space:]]/, "", name)
      if (!classHidden && !hidden) {
        if (mapping && !operation) print "missing " name " " NR
        if (operation && !mapping) print "orphan " name " " NR
      }
      reset()
    }
  }
' "$file_path")

[ -z "$problems" ] && exit 0

{
  echo "OpenAPI 검사(check-openapi.sh): ${path##*/}의 요청 매핑 메서드와 @Operation이 맞지 않습니다."
  printf '%s\n' "$problems" | while read -r kind name line; do
    case "$kind" in
      missing) echo "- ${name}() (${line}줄): 매핑 어노테이션은 있지만 @Operation이 없습니다." ;;
      orphan) echo "- ${name}() (${line}줄): @Operation은 있지만 매핑 어노테이션이 없습니다." ;;
    esac
  done
  echo "모든 API는 @Operation과 응답 코드(@ApiResponse)를 문서화해야 합니다(NFR-04, backend/CLAUDE.md OpenAPI 규칙). 문서에서 뺄 메서드라면 @Hidden을 붙이세요."
} >&2
exit 2
