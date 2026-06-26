#!/bin/bash
# Stop 훅 — Claude 작업 완료 시 변경사항을 자동으로 커밋한다. push는 하지 않는다.
#
# [재귀 방지]
# Stop 훅 내부에서 `git commit`이 일어나도 새 Claude 세션이 생기지 않으므로 안전하다.
# 단, 이 스크립트 자체가 `claude --print`를 호출하는 경우 중첩 Stop 훅이 실행될 수 있어
# 락 파일로 재진입을 차단한다.

LOCK="/tmp/claude-os-auto-commit.lock"
[ -f "$LOCK" ] && exit 0
touch "$LOCK"
trap "rm -f '$LOCK'" EXIT

# 변경사항 스테이징
git add -A 2>/dev/null

# 스테이지에 올라간 내용이 없으면 조용히 종료
git diff --cached --quiet && exit 0

FILES=$(git diff --cached --name-only)
FCOUNT=$(echo "$FILES" | grep -c .)

# ── 변경 파일 패턴으로 type / scope 추론 ──────────────────────────────────
TYPE="chore"
SCOPE="misc"

# 가장 많이 변경된 영역을 scope로 선택
HOOK_CNT=$(echo "$FILES" | grep -c "hooks/" || true)
SKILL_CNT=$(echo "$FILES" | grep -c "skills/" || true)
LOG_CNT=$(echo "$FILES"   | grep -c "logs/"   || true)

if   [ "${SKILL_CNT:-0}" -gt "${HOOK_CNT:-0}" ] && [ "${SKILL_CNT:-0}" -gt "${LOG_CNT:-0}" ]; then
    TYPE="feat"; SCOPE="skill"
elif [ "${HOOK_CNT:-0}"  -gt "${SKILL_CNT:-0}" ] && [ "${HOOK_CNT:-0}"  -gt "${LOG_CNT:-0}" ]; then
    TYPE="feat"; SCOPE="hook"
elif [ "${LOG_CNT:-0}"   -gt 0 ]; then
    SCOPE="log"
fi

# 개별 파일 규칙 (위 영역 판단보다 구체적일 때 덮어씀)
echo "$FILES" | grep -q "settings.json"  && SCOPE="config"
echo "$FILES" | grep -q "CLAUDE.md"      && TYPE="docs" && SCOPE="claude"
echo "$FILES" | grep -q ".gitignore"     && TYPE="chore" && SCOPE="config"

# ── 커밋 메시지 조합 ─────────────────────────────────────────────────────
FIRST=$(echo "$FILES" | head -1 | xargs basename 2>/dev/null | sed 's/\.[^.]*$//')
if [ "$FCOUNT" -gt 1 ]; then
    DESC="$FIRST 외 $((FCOUNT - 1))개"
else
    DESC="$FIRST"
fi

MSG="${TYPE}(${SCOPE}): ${DESC}"

# ── 커밋 ────────────────────────────────────────────────────────────────
if git commit -m "$MSG

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>" 2>/dev/null; then
    printf '{"systemMessage":"✅ 자동 커밋: %s"}' "$MSG"
else
    printf '{"systemMessage":"⚠️ 자동 커밋 실패 — git 로그를 확인하세요"}'
fi
