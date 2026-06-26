#!/bin/bash
# Stop 훅 — 안전망 역할만 한다.
# 각 에이전트(planner/designer/backend-dev/frontend-dev)가 작업 완료 시 직접 커밋하므로
# 이 훅은 에이전트가 커밋을 빠뜨렸을 때만 동작한다.
# 메시지는 의도적으로 단순하게 유지한다 — 좋은 커밋 메시지는 에이전트가 직접 써야 한다.

LOCK="/tmp/claude-os-auto-commit.lock"
[ -f "$LOCK" ] && exit 0
touch "$LOCK"
trap "rm -f '$LOCK'" EXIT

# 미추적 파일 포함 전체 스테이징
git add -A 2>/dev/null

# 스테이지에 올라간 내용이 없으면 조용히 종료
git diff --cached --quiet && exit 0

FILES=$(git diff --cached --name-only)

# ── 안전 검사 ──────────────────────────────────────────────────────────
DANGEROUS=$(echo "$FILES" | grep -Ei \
    '\.env$|\.env\.|secret|credential|password|private\.key|id_rsa|\.pem$' \
    || true)

if [ -n "$DANGEROUS" ]; then
    git reset HEAD -- $DANGEROUS 2>/dev/null
    printf '{"systemMessage":"🚫 안전망 커밋 중단 — 민감 파일 감지: %s"}' \
        "$(echo "$DANGEROUS" | tr '\n' ' ')"
    exit 0
fi

# ── 변경 파일 목록을 본문에 포함해 커밋 ────────────────────────────────
FCOUNT=$(echo "$FILES" | grep -c . || echo 0)
BODY=$(echo "$FILES" | sed 's/^/- /')

git commit -m "$(cat <<EOF
chore: 미커밋 변경사항 저장 (${FCOUNT}개 파일)

${BODY}

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)" 2>/dev/null \
    && printf '{"systemMessage":"⚠️ 안전망 커밋 실행됨 — 에이전트가 커밋을 빠뜨렸을 수 있습니다 (%d개 파일)"}' "$FCOUNT" \
    || printf '{"systemMessage":"⚠️ 안전망 커밋 실패 — git 로그를 확인하세요"}'
