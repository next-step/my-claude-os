#!/usr/bin/env bash
#
# tests/smoke.sh — L3: headless 통합 스모크 테스트 (비결정적, 외부 의존)
#
# "진짜로 동작하나?"를 확인한다. claude 를 headless(-p)로 띄워 /capture 를 실제 실행하고,
# Notion DB 에 draft 가 생성됐는지 확인한 뒤, 만든 테스트 항목을 아카이브로 정리한다.
#
# LLM·네트워크·자격증명에 의존하므로 다음이 없으면 깔끔하게 SKIP 한다 (실패 아님):
#   - claude CLI 없음            → SKIP
#   - .claude/data/notion.json 없음 → SKIP  (CI엔 비밀값이 없으니 정상적으로 건너뜀)
#   - jq 없음                    → SKIP
#
# 종료 코드: 통과/스킵 → 0, 검증 실패 → 1
#
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

ok()   { printf '  \033[32m✓\033[0m %s\n' "$1"; }
bad()  { printf '  \033[31m✗\033[0m %s\n' "$1"; }
skip() { printf '  \033[33m–\033[0m %s\n' "$1"; echo "L3 결과: SKIP"; exit 0; }

echo "── L3: headless 통합 스모크 (/capture) ─────────"

# ── 가드: 필요한 도구·자격증명 확인 ────────────────────────────
command -v claude >/dev/null 2>&1 || skip "claude CLI 없음 → SKIP"
command -v jq     >/dev/null 2>&1 || skip "jq 없음 → SKIP"
NOTION_JSON=".claude/data/notion.json"
[[ -f "$NOTION_JSON" ]] || skip "notion.json 없음 (비밀값 부재) → SKIP"

TOKEN="$(jq -r '.token' "$NOTION_JSON")"
NOTION_VERSION="2022-06-28"
NOTION_SH=".claude/skills/_shared/notion.sh"

# 입력 키워드는 "평범한 할일"이어야 한다.
#   - __SMOKE_TEST__ 같은 노골적 이름 → 똑똑한 LLM 이 "테스트구나" 하고 저장을 건너뛴다.
#   - 게다가 스킬이 입력을 정규화하며 접미사 숫자 등을 떼어내므로, 내가 넣은 고유
#     문자열이 제목으로 그대로 살아남는다는 보장이 없다.
# 그래서 식별을 "제목 매칭"이 아니라 "실행 전후 draft id 집합의 차이"로 한다.
# → 제목이 어떻게 변형되든, 새로 생긴 draft 를 id 로 정확히 집어낸다.
KEYWORD="우유사기"
echo "  입력 키워드: $KEYWORD (식별은 전/후 id diff 로)"

# ── 1) 실행 전 draft id 스냅샷 ────────────────────────────────
BEFORE="$("$NOTION_SH" read draft 2>/dev/null | jq -r '.[].id' | sort)"

# ── 2) headless 로 /capture 실행 ──────────────────────────────
# 스킬이 Bash(notion.sh)·Agent·Read 를 써야 하므로 권한 프롬프트를 건너뛴다.
echo "  [1] claude -p \"/capture $KEYWORD\" 실행 중... (수십 초 소요)"
if claude -p "/capture $KEYWORD" --dangerously-skip-permissions >/tmp/smoke_out.txt 2>&1; then
  ok "claude 실행 종료코드 0"
else
  bad "claude 실행 실패 (출력은 /tmp/smoke_out.txt)"
  sed 's/^/      /' /tmp/smoke_out.txt | head -20
  echo "L3 결과: 1 fail"
  exit 1
fi

# ── 3) 새로 생긴 draft 를 id diff 로 식별 ──────────────────────
echo "  [2] Notion draft 생성 확인 (전/후 diff)"
AFTER_JSON="$("$NOTION_SH" read draft 2>/dev/null)"
AFTER="$(echo "$AFTER_JSON" | jq -r '.[].id' | sort)"
NEW_IDS="$(comm -13 <(echo "$BEFORE") <(echo "$AFTER"))"
NEW_COUNT="$(echo "$NEW_IDS" | grep -c . || true)"

FAIL=0
PAGE_ID=""
if [[ "$NEW_COUNT" -eq 1 ]]; then
  PAGE_ID="$NEW_IDS"
  TITLE="$(echo "$AFTER_JSON"   | jq -r --arg i "$PAGE_ID" '.[]|select(.id==$i)|.title')"
  CATEGORY="$(echo "$AFTER_JSON" | jq -r --arg i "$PAGE_ID" '.[]|select(.id==$i)|.category')"
  ok "새 draft 1건 생성됨 (title=\"$TITLE\", id=${PAGE_ID:0:8}…)"
  if [[ -n "$CATEGORY" && "$CATEGORY" != "null" ]]; then
    ok "카테고리 분류됨: $CATEGORY"
  else
    bad "카테고리가 비어있음 — classifier 에이전트 단계 확인 필요"
    FAIL=1
  fi
elif [[ "$NEW_COUNT" -eq 0 ]]; then
  bad "새 draft 가 없음 — 오케스트레이션이 notion.sh write 까지 못 갔다"
  FAIL=1
else
  bad "새 draft 가 ${NEW_COUNT}건 — 예상은 1건 (동시 실행/잔여 데이터 확인)"
  FAIL=1
fi

# ── 3) 정리: 만든 테스트 항목을 아카이브 ───────────────────────
if [[ -n "$PAGE_ID" && "$PAGE_ID" != "null" ]]; then
  echo "  [3] 테스트 항목 정리 (아카이브)"
  ARCH="$(curl -s -X PATCH "https://api.notion.com/v1/pages/$PAGE_ID" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Notion-Version: $NOTION_VERSION" \
    -H "Content-Type: application/json" \
    -d '{"archived": true}' | jq -r '.archived // false')"
  [[ "$ARCH" == "true" ]] && ok "아카이브 완료 (DB 오염 없음)" \
                          || bad "아카이브 실패 — Notion 에서 id=${PAGE_ID} 수동 삭제 필요"
fi

echo "────────────────────────────────────────────────"
if [[ "$FAIL" -eq 0 ]]; then
  echo "L3 결과: PASS"
else
  echo "L3 결과: 1 fail"
fi
exit "$FAIL"
