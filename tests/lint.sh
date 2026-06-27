#!/usr/bin/env bash
#
# tests/lint.sh — L1: 정적 검증 (결정적, 외부 의존 없음)
#
# 무엇을 검사하나 (런타임이 아니라 "구조"가 깨졌는지를 본다):
#   1) 모든 SKILL.md 에 name / description frontmatter 가 있는가
#   2) 스킬·에이전트 md 가 참조하는 .claude/...(md|sh|js) 경로가 실제로 존재하는가
#      → 파일명을 바꾸면 런타임에야 깨지는 "끊어진 링크"를 커밋 전에 잡는다
#   3) 훅 JS 가 문법적으로 유효한가 (node --check)
#   4) 모든 셸 스크립트가 문법적으로 유효한가 (bash -n)
#   5) 데이터 JSON 이 valid JSON 이고 필수 키를 갖는가 (없으면 SKIP — CI에선 비밀값 부재)
#
# 종료 코드: 실패 0건 → 0, 1건 이상 → 1
#
set -uo pipefail

# 레포 루트로 이동 (이 스크립트가 어디서 호출되든 동일하게 동작)
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

PASS=0
FAIL=0
ok()   { PASS=$((PASS+1)); printf '  \033[32m✓\033[0m %s\n' "$1"; }
bad()  { FAIL=$((FAIL+1)); printf '  \033[31m✗\033[0m %s\n' "$1"; }
skip() { printf '  \033[33m–\033[0m %s (SKIP)\n' "$1"; }

echo "── L1: 정적 검증 ──────────────────────────────"

# ── 1) SKILL.md frontmatter ────────────────────────────────────
echo "[1] SKILL.md frontmatter (name / description)"
while IFS= read -r f; do
  # 첫 frontmatter 블록(--- ... ---)만 추출해서 키 존재 확인
  fm="$(awk 'NR==1&&$0=="---"{inb=1;next} inb&&$0=="---"{exit} inb{print}' "$f")"
  if [[ -z "$fm" ]]; then
    bad "$f — frontmatter 블록 없음"
    continue
  fi
  missing=""
  echo "$fm" | grep -qE '^name:[[:space:]]*\S'        || missing+="name "
  echo "$fm" | grep -qE '^description:[[:space:]]*\S' || missing+="description "
  if [[ -n "$missing" ]]; then
    bad "$f — 누락: $missing"
  else
    ok "$f"
  fi
done < <(find .claude/skills -name SKILL.md | sort)

# ── 2) 참조 경로 링크 무결성 ───────────────────────────────────
# 스킬·에이전트 md 가 본문에서 언급하는 .claude/...(md|sh|js) 가 실재하는지.
# .claude/data/ 는 비밀값(gitignore)이라 CI에 없으므로 제외한다.
echo "[2] 참조 파일 링크 무결성"
broken=0
checked=0
while IFS= read -r src; do
  while IFS= read -r ref; do
    [[ -z "$ref" ]] && continue
    [[ "$ref" == .claude/data/* ]] && continue   # 비밀값 — 검사 제외
    checked=$((checked+1))
    if [[ ! -e "$ref" ]]; then
      bad "$src → 끊어진 참조: $ref"
      broken=$((broken+1))
    fi
  done < <(grep -oE '\.claude/[A-Za-z0-9_./-]+\.(md|sh|js)' "$src" | sort -u)
done < <(find .claude/skills -name '*.md' | sort)
if [[ "$broken" -eq 0 ]]; then
  ok "참조 ${checked}개 모두 존재"
fi

# ── 3) 훅 JS 문법 검사 ─────────────────────────────────────────
echo "[3] 훅 JS 문법 (node --check)"
while IFS= read -r js; do
  if node --check "$js" 2>/dev/null; then
    ok "$js"
  else
    bad "$js — 문법 오류"
    node --check "$js" 2>&1 | sed 's/^/      /'
  fi
done < <(find .claude/hooks -name '*.js' 2>/dev/null | sort)

# ── 4) 셸 스크립트 문법 검사 ──────────────────────────────────
echo "[4] 셸 스크립트 문법 (bash -n)"
while IFS= read -r sh; do
  if bash -n "$sh" 2>/dev/null; then
    ok "$sh"
  else
    bad "$sh — 문법 오류"
    bash -n "$sh" 2>&1 | sed 's/^/      /'
  fi
done < <(find .claude tests -name '*.sh' 2>/dev/null | sort)

# ── 5) 데이터 JSON 스키마 (있을 때만) ─────────────────────────
echo "[5] 데이터 JSON 유효성 (비밀값 — 있을 때만)"
check_json() { # $1=path  $2..=required keys
  local p="$1"; shift
  if [[ ! -f "$p" ]]; then skip "$p"; return; fi
  if ! jq empty "$p" 2>/dev/null; then bad "$p — invalid JSON"; return; fi
  local miss=""
  for k in "$@"; do
    jq -e --arg k "$k" 'has($k) and (.[$k]|tostring|length>0)' "$p" >/dev/null 2>&1 || miss+="$k "
  done
  [[ -n "$miss" ]] && bad "$p — 키 누락: $miss" || ok "$p"
}
check_json .claude/data/notion.json   token database_id
check_json .claude/data/telegram.json bot_token chat_id

# settings.local.json 은 항상 검사 (커밋되는 설정)
if jq empty .claude/settings.local.json 2>/dev/null; then
  ok ".claude/settings.local.json — valid JSON"
else
  bad ".claude/settings.local.json — invalid JSON"
fi

echo "────────────────────────────────────────────────"
echo "L1 결과: ${PASS} pass / ${FAIL} fail"
[[ "$FAIL" -eq 0 ]]
