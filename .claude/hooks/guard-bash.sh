#!/usr/bin/env bash
# PreToolUse(Bash): 운영 환경 접속과 되돌릴 수 없는 명령을 막는다.
# 막을 때는 stderr에 이유와 대안을 쓰고 exit 2로 끝낸다.
# 일반 명령이 느려지지 않도록 bash 내장 기능만 쓰고, git 호출은 git push일 때만 한다.

input=$(cat)
if ! command -v jq >/dev/null 2>&1; then
  echo "guard-bash: jq가 없어 명령 검사를 건너뜀" >&2
  exit 1
fi
command=$(printf '%s' "$input" | jq -r '.tool_input.command // empty' 2>/dev/null)
[ -z "$command" ] && exit 0
lower=${command,,}

block() {
  echo "$1" >&2
  exit 2
}

# 1) 운영 호스트·DB 이름(팀 명명 규칙)
if [[ $lower == *eduhub-prod* || $lower == *prod-db* ]]; then
  block "운영 환경(eduhub-prod, prod-db)에 접속하거나 다루는 명령은 Claude가 실행하지 않는다. 개발 DB(eduhub)나 테스트 DB(eduhub_test)로 확인하고, 운영 작업은 사람이 런북에 따라 직접 실행할 것."
fi

# 2) Flyway clean: 스키마의 모든 객체를 지운다
if [[ $lower == *flywayclean* ]]; then
  block "flywayClean은 스키마를 통째로 지워 되돌릴 수 없다. 마이그레이션 문제는 새 버전 파일로 고치고, 테스트 DB를 비워야 하면 사람이 직접 실행할 것."
fi

# 3) git push 검사: &&, ||, ;, |, 줄바꿈으로 나눈 각 명령을 본다
[[ $lower == *push* ]] || exit 0

segments=${command//&&/$'\n'}
segments=${segments//||/$'\n'}
segments=${segments//;/$'\n'}
segments=${segments//|/$'\n'}

push_re='(^|[[:space:]])git(([[:space:]]+-[cC][[:space:]]+[^[:space:]]+)|([[:space:]]+--[^[:space:]]+))*[[:space:]]+push([[:space:]]|$)(.*)'

while IFS= read -r segment; do
  [[ $segment =~ $push_re ]] || continue
  read -r -a args <<<"${BASH_REMATCH[6]}"

  positionals=()
  for arg in "${args[@]}"; do
    case "$arg" in
      --force | --force=* | --force-with-lease | --force-with-lease=* | --force-if-includes | --mirror)
        block "강제 푸시($arg)는 원격 이력을 덮어써 되돌리기 어렵다. 새 커밋으로 고치거나 git revert를 쓰고, 꼭 필요하면 사람이 직접 실행할 것." ;;
      --*) ;;
      -*f*)
        block "강제 푸시($arg)는 원격 이력을 덮어써 되돌리기 어렵다. 새 커밋으로 고치거나 git revert를 쓰고, 꼭 필요하면 사람이 직접 실행할 것." ;;
      -*) ;;
      +*)
        block "+refspec($arg)은 강제 푸시와 같다. 새 커밋으로 고치거나 git revert를 쓸 것." ;;
      *) positionals+=("$arg") ;;
    esac
  done

  # 첫 위치 인자는 원격, 나머지는 refspec
  for refspec in "${positionals[@]:1}"; do
    dst=${refspec##*:}
    dst=${dst#refs/heads/}
    if [[ $dst == main ]]; then
      block "main에 직접 푸시하지 않는다. 작업 브랜치(<type>/<설명>)로 푸시하고 PR로 병합할 것."
    fi
  done

  # refspec 없이 git push만 하면 현재 브랜치가 올라간다
  if [ "${#positionals[@]}" -le 1 ]; then
    current=$(git symbolic-ref --quiet --short HEAD 2>/dev/null)
    if [ "$current" = "main" ]; then
      block "현재 브랜치가 main이라 이 git push는 main에 직접 푸시한다. 작업 브랜치를 만들어 푸시하고 PR로 병합할 것."
    fi
  fi
done <<<"$segments"

exit 0
