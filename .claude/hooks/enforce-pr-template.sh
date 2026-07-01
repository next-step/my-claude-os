#!/usr/bin/env bash
# PreToolUse(Bash) 훅: `gh pr create` 의 본문이 PR 템플릿 틀을 따르는지 "무조건" 강제한다.
# 필수 섹션(## 요약 / ## 변경 내용 / ## 테스트)이 본문에 없으면 차단(exit 2)하고
# 그 사유를 에이전트에게 돌려준다. (구조만 검사 — 내용 품질은 스킬+사람 게이트의 몫)
#
# 전제: /pr 스킬이 본문을 인라인 `--body "..."` 로 넘겨, 명령 문자열에서 섹션을 검사할 수 있어야 한다.

set -euo pipefail

cmd=$(jq -r '.tool_input.command // empty' 2>/dev/null || true)

# 실제로 `gh pr create`를 "명령으로 실행"할 때만 관여한다.
# (커밋 메시지 등 문자열 안에 'gh pr create'가 언급된 경우는 무시 — 줄 시작 또는 ;&| 뒤에 올 때만 명령으로 간주)
printf '%s' "$cmd" | grep -qE '(^|[;&|])[[:space:]]*gh pr create' || exit 0

missing=""
# 필수: 섹션 헤더 + 흐름도(mermaid 블록)까지 항상 포함되어야 한다.
for s in '## 요약' '## 개발 흐름' '## 변경 내용' '## 테스트' 'mermaid'; do
  printf '%s' "$cmd" | grep -qF "$s" || missing="$missing $s"
done

if [ -n "$missing" ]; then
  echo "PR 본문이 템플릿을 따르지 않습니다. 누락:$missing" >&2
  echo ".github/pull_request_template.md 형식(## 요약 / ## 개발 흐름[mermaid] / ## 변경 내용 / ## 테스트)으로 본문을 다시 작성해 gh pr create 하세요." >&2
  exit 2
fi

exit 0
