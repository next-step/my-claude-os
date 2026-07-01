#!/usr/bin/env bash
# Skill 호출 통계 기록용 PreToolUse hook.
# stdin 으로 들어온 hook JSON에서 호출된 skill 이름을 뽑아
# ~/.claude/skill-stats.json 에 누적 횟수와 마지막 호출 시각을 기록한다.
# 어떤 경우에도 Skill 도구 실행을 막지 않도록 항상 exit 0 한다.

STATS="${HOME}/.claude/skill-stats.json"

# stdin 의 hook payload 에서 skill 이름 추출 (예: {"tool_input":{"skill":"commit"}})
skill="$(jq -r '.tool_input.skill // empty' 2>/dev/null)"

# skill 이름이 없으면 (다른 도구이거나 파싱 실패) 조용히 종료
[ -z "$skill" ] && exit 0

# 통계 파일이 없으면 빈 객체로 초기화
[ -f "$STATS" ] || printf '{}\n' > "$STATS"

now="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

# 원자적 갱신: 임시 파일에 쓴 뒤 mv
tmp="$(mktemp "${TMPDIR:-/tmp}/skill-stats.XXXXXX")"
if jq --arg s "$skill" --arg t "$now" '
      .[$s].count = ((.[$s].count // 0) + 1)
    | .[$s].last  = $t
  ' "$STATS" > "$tmp" 2>/dev/null; then
  mv "$tmp" "$STATS"
else
  rm -f "$tmp"
fi

exit 0
