#!/bin/bash
# Stop 훅 — Claude 작업 완료 시 변경사항을 안전하게 자동 커밋한다. push는 하지 않는다.

LOCK="/tmp/claude-os-auto-commit.lock"
[ -f "$LOCK" ] && exit 0
touch "$LOCK"
trap "rm -f '$LOCK'" EXIT

# 변경사항 스테이징
git add -A 2>/dev/null

# 스테이지에 올라간 내용이 없으면 조용히 종료
git diff --cached --quiet && exit 0

FILES=$(git diff --cached --name-only)

# ── 안전 검사 ────────────────────────────────────────────────────────────
# 민감 파일 패턴 — 이 파일이 스테이지에 있으면 커밋 전체를 중단한다
DANGEROUS=$(echo "$FILES" | grep -Ei \
    '\.env$|\.env\.|secret|credential|password|private.key|id_rsa|\.pem$|token' \
    || true)

if [ -n "$DANGEROUS" ]; then
    # 스테이지 되돌리기
    git reset HEAD -- $DANGEROUS 2>/dev/null
    printf '{"systemMessage":"🚫 자동 커밋 중단 — 민감 파일 감지: %s"}' "$(echo "$DANGEROUS" | tr '\n' ' ')"
    exit 0
fi

# 파일 내용 중 개인정보 패턴 검사 (staged diff 기준)
SENSITIVE_CONTENT=$(git diff --cached | grep -E \
    '^\+.*(password\s*=|api_key\s*=|secret\s*=|token\s*=|[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,})' \
    | grep -v '^\+\+\+' || true)

if [ -n "$SENSITIVE_CONTENT" ]; then
    git reset 2>/dev/null
    printf '{"systemMessage":"🚫 자동 커밋 중단 — 내용에 민감 정보 의심 패턴 감지. 수동으로 확인 후 커밋하세요."}'
    exit 0
fi

# ── 커밋 메시지 추론 ─────────────────────────────────────────────────────
FCOUNT=$(echo "$FILES" | grep -c .)

TYPE="chore"; SCOPE="misc"

HOOK_CNT=$(echo "$FILES"  | grep -c "hooks/"  || true)
SKILL_CNT=$(echo "$FILES" | grep -c "skills/" || true)

if   [ "${SKILL_CNT:-0}" -gt "${HOOK_CNT:-0}" ]; then TYPE="feat"; SCOPE="skill"
elif [ "${HOOK_CNT:-0}"  -gt "${SKILL_CNT:-0}" ]; then TYPE="feat"; SCOPE="hook"
fi

echo "$FILES" | grep -q "settings.json" && SCOPE="config"
echo "$FILES" | grep -q "CLAUDE.md"     && TYPE="docs"  && SCOPE="claude"
echo "$FILES" | grep -q ".gitignore"    && TYPE="chore" && SCOPE="config"

FIRST=$(echo "$FILES" | head -1 | xargs basename 2>/dev/null | sed 's/\.[^.]*$//')
[ "$FCOUNT" -gt 1 ] && DESC="$FIRST 외 $((FCOUNT - 1))개" || DESC="$FIRST"
MSG="${TYPE}(${SCOPE}): ${DESC}"

# ── 커밋 ────────────────────────────────────────────────────────────────
if git commit -m "$MSG

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>" 2>/dev/null; then
    printf '{"systemMessage":"✅ 자동 커밋: %s"}' "$MSG"
else
    printf '{"systemMessage":"⚠️ 자동 커밋 실패 — git 로그를 확인하세요"}'
fi
