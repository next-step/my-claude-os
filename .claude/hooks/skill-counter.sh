#!/usr/bin/env bash
#
# skill-counter.sh — Skill 호출 카운터 훅
#
# Claude Code가 Skill 도구를 호출하기 직전(PreToolUse)에 이 스크립트를 실행한다.
# stdin 으로 훅 입력 JSON이 들어오며, 그 안의 스킬 이름을 뽑아
# .claude/skill-stats.json 에 호출 횟수를 누적 기록한다.
#
# 설계 원칙
#  - PreToolUse 훅은 실패(비정상 종료)하면 도구 호출 자체를 막을 수 있으므로,
#    어떤 상황에서도 마지막엔 반드시 exit 0 으로 끝내 "절대 방해하지 않는다".
#  - 스크립트 위치를 기준으로 통계 파일 경로를 계산하므로, 어디서 실행하든 동작한다.

# 이 스크립트가 있는 .claude/hooks 디렉터리 → 상위(.claude)에 통계 파일을 둔다
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
STATS_FILE="$SCRIPT_DIR/../skill-stats.json"

# 훅 입력(JSON 한 덩어리)을 통째로 읽는다
INPUT="$(cat)"

# Skill 도구의 입력 형태: { "tool_input": { "skill": "스킬이름", "args": "..." } }
# 스킬 이름을 추출한다. jq가 없거나 파싱 실패해도 빈 문자열로 처리.
SKILL_NAME="$(printf '%s' "$INPUT" | jq -r '.tool_input.skill // empty' 2>/dev/null)"

# 스킬 이름이 없으면(Skill 도구가 아니거나 비정상 입력) 조용히 종료
if [ -z "$SKILL_NAME" ]; then
  exit 0
fi

# 통계 파일이 없으면 기본 구조로 초기화
if [ ! -f "$STATS_FILE" ]; then
  echo '{"skills":{},"total":0}' > "$STATS_FILE"
fi

# 호출 시각(UTC)
NOW="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

# 해당 스킬 카운트 +1, 마지막 호출 시각 갱신, 전체 합계 +1
# 원자적 갱신을 위해 임시파일에 쓴 뒤 교체한다.
TMP="$(mktemp)"
if jq \
  --arg s "$SKILL_NAME" \
  --arg t "$NOW" \
  '.skills[$s].count = ((.skills[$s].count // 0) + 1)
   | .skills[$s].last_called = $t
   | .total = ((.total // 0) + 1)' \
  "$STATS_FILE" > "$TMP" 2>/dev/null; then
  mv "$TMP" "$STATS_FILE"
else
  # 갱신 실패 시 임시파일만 정리하고 도구 호출은 그대로 진행시킨다
  rm -f "$TMP"
fi

exit 0
