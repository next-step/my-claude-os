#!/usr/bin/env bash
# SessionStart 훅: 세션이 시작될 때 진행 중인 OS 작업이 있으면 자동으로 알려준다.
#
# 동작 방식:
#   - Claude Code가 세션 시작 시 이 스크립트를 실행한다.
#   - .claude/os/state.md 의 stage 가 done 이 아니면(=진행 중),
#     stdout 으로 한 줄 안내를 내보낸다. SessionStart 훅의 stdout 은 세션 컨텍스트에 추가되어
#     Claude 가 "이어서 진행할 작업이 있다"는 사실을 인지하게 된다.
#   - 진행 중인 작업이 없으면(파일 없음 또는 stage: done) 아무것도 출력하지 않는다(조용히 종료).
#
# OS 원칙 연계: "상태는 항상 파일에 — 컨텍스트가 리셋돼도 0단계에서 이어갈 수 있어야 한다."
#   이 훅은 그 이어가기를 사람이 기억하지 않아도 되도록 자동화한다.

set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
STATE_FILE="$DIR/os/state.md"

# 상태 파일이 없으면 진행 중인 작업 없음 → 조용히 종료
[[ -f "$STATE_FILE" ]] || exit 0

# stage 값 추출 (예: "stage: 3   # ..." → "3")
stage="$(grep -m1 '^stage:' "$STATE_FILE" 2>/dev/null | sed -E 's/^stage:[[:space:]]*([^[:space:]#]+).*/\1/')"

# stage 가 비었거나 done 이면 알릴 것 없음
[[ -n "$stage" && "$stage" != "done" ]] || exit 0

# 요구사항 한 줄 요약 추출(있으면)
summary="$(grep -m1 '한 줄 요약:' "$STATE_FILE" 2>/dev/null | sed -E 's/.*한 줄 요약:[[:space:]]*//')"

echo "⚠️ 진행 중인 OS 작업이 있습니다 — stage: ${stage}."
[[ -n "$summary" ]] && echo "   요구사항: ${summary}"
echo "   이어서 하려면 /os 로 ${stage}단계부터, 현황만 보려면 /os-status 를 사용하세요."

exit 0
