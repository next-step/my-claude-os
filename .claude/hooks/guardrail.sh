#!/usr/bin/env bash
#
# guardrail.sh — 위험한 명령 차단 훅
#
# Claude Code가 Bash 도구를 호출하기 직전(PreToolUse)에 실행된다.
# stdin 으로 훅 입력 JSON이 들어오며, 실행하려는 명령을 검사해
# 되돌리기 어려운 git 작업이면 차단한다.
#
# 차단 방법: exit 2 로 끝내면 Claude Code가 도구 호출을 막고, stderr에 적은
# 이유를 모델에게 돌려준다. 그 외에는 exit 0 으로 통과시킨다.
#
# 여기서 "강제"하는 건 모호하지 않은 것만이다(force push, main 직접 push).
# 비밀값 커밋·프로덕션 조작 같은 건 오탐이 커서 CLAUDE.md 선언으로만 다룬다.

INPUT="$(cat)"

# Bash 도구의 입력 형태: { "tool_input": { "command": "..." } }
CMD="$(printf '%s' "$INPUT" | jq -r '.tool_input.command // empty' 2>/dev/null)"

# 명령이 없으면(Bash가 아니거나 비정상 입력) 조용히 통과
if [ -z "$CMD" ]; then
  exit 0
fi

# git push 가 들어간 명령만 검사 대상
if printf '%s' "$CMD" | grep -qE 'git[[:space:]]+push'; then

  # 1) force push — 단 --force-with-lease(안전판)는 허용
  if printf '%s' "$CMD" | grep -qE -- '(^|[[:space:]])(-f|--force)([[:space:]]|$)'; then
    echo "차단: force push 는 이력을 덮어써 되돌리기 어렵다. 필요하면 --force-with-lease 를 쓰거나 사용자에게 먼저 확인하라." >&2
    exit 2
  fi

  # 2) main/master 직접 push — 토큰 경계로 매칭(maintenance 등 오탐 방지)
  if printf '%s' "$CMD" | grep -qE -- '(^|[[:space:]:/])(main|master)([[:space:]]|$)'; then
    echo "차단: main/master 에 직접 push 하지 않는다. 브랜치에서 작업하고 PR로 올려라." >&2
    exit 2
  fi
fi

exit 0
