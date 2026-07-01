#!/usr/bin/env bash
# holdings-journal-guard — stock-os 커널 §1 "기록 없는 매매 없음" 보강 hook.
#
# Claude Code의 PostToolUse(Write|Edit) hook으로 등록된다(.claude/settings.json).
# holdings.md가 편집되면 trade-journal도 함께 갱신했는지 리마인드한다.
# 자동 강제(차단)가 아니라 nudge다 — journal 갱신이 매매기록 흐름상 뒤따르는 게 정상이라,
# 편집을 막는 대신 exit 2로 리마인드를 Claude 루프에 피드백해 후속 조치를 유도한다.
#
# 입력: stdin으로 hook JSON (tool_name, tool_input.file_path 등).
# 동작: 편집 대상이 02_portfolio/holdings.md면 exit 2 + stderr 리마인드. 그 외엔 exit 0 무출력.

input="$(cat)"

# tool_input.file_path 추출 — jq 있으면 정확히, 없으면 grep 폴백.
if command -v jq >/dev/null 2>&1; then
  file_path="$(printf '%s' "$input" | jq -r '.tool_input.file_path // empty')"
else
  file_path="$(printf '%s' "$input" | grep -o '"file_path"[[:space:]]*:[[:space:]]*"[^"]*"' | head -1 | sed 's/.*"file_path"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/')"
fi

# holdings.md 편집이 아니면 조용히 통과.
case "$file_path" in
  */02_portfolio/holdings.md|02_portfolio/holdings.md) ;;
  *) exit 0 ;;
esac

# holdings.md 편집 감지 → 리마인드(exit 2면 stderr가 Claude에게 전달됨).
cat >&2 <<'MSG'
⚠️ holdings.md 변경 감지 — stock-os 커널 §1 "기록 없는 매매 없음".
이 매매를 03_journal/trade-journal.md에도 기록했는지 확인하세요
(감정 점수·근거·매도면 결과=실질 기준). 아직이면 `log-trade` 스킬로 holdings+journal을
함께 갱신하세요. 이미 기록했다면 이 알림은 무시해도 됩니다.
MSG
exit 2
