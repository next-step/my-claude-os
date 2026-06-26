---
name: auto-commit
description: |
  변경사항을 확인 없이 즉시 자동 커밋하는 스킬. 사용자 확인 없이 바로 커밋한다.
  github-commit 스킬과 달리 단계별 확인 없이 빠르게 진행한다.

  다음 표현에 반드시 사용한다:
  - "자동 커밋", "바로 커밋해줘", "확인 없이 커밋해줘"
  - "auto-commit", "auto commit"
  - "지금 바로 저장해줘", "그냥 커밋해줘"
---

# Auto Commit 스킬

사용자 확인 없이 변경사항을 즉시 커밋한다. push는 하지 않는다.

## 워크플로우

### 1단계: 상태 및 diff 확인

```bash
git status --short
git diff --stat HEAD
```

변경사항이 없으면 "커밋할 변경사항이 없습니다."를 안내하고 종료.

### 2단계: 커밋 메시지 결정

변경된 파일 목록과 diff를 보고 Conventional Commits 형식으로 메시지를 직접 결정한다.
사용자에게 묻지 않는다.

**scope 결정 기준:**
- `.claude/hooks/` 변경 → `hook`
- `.claude/skills/` 변경 → `skill`
- `.claude/settings.json` 변경 → `config`
- `CLAUDE.md` 변경 → `claude`
- 여러 영역 동시 변경 → 가장 많이 변경된 영역

### 3단계: 즉시 커밋

```bash
git add -A
git commit -m "$(cat <<'EOF'
<결정한 커밋 메시지>

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

### 4단계: 결과 안내

```
✅ 커밋 완료: <커밋 메시지>
   브랜치: <브랜치명> | push는 수동으로 진행하세요.
```

## 주의

- 민감한 파일(`.env`, `*secret*`)이 있으면 예외적으로 경고 후 제외
- push는 절대 자동으로 하지 않음
- main/master 브랜치에서는 실행 전 한 번 확인
