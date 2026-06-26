---
name: github-commit
description: |
  변경사항을 커밋하는 스킬. 요청 문구에 따라 두 가지 모드로 동작한다.

  【일반 모드】단계별 확인 후 커밋 + push 가능
  - "커밋해줘", "깃허브에 올려줘", "push해줘", "변경사항 저장해줘"
  - "git commit", "git push" 관련 요청

  【빠른 모드】확인 없이 즉시 커밋, push 없음
  - "자동 커밋", "바로 커밋해줘", "확인 없이 커밋해줘"
  - "auto commit", "auto-commit", "그냥 커밋해줘"
---

# GitHub Commit 스킬

요청 문구로 모드를 판단한다.
- "자동", "바로", "확인 없이", "그냥" 포함 → **빠른 모드**
- 그 외 → **일반 모드**

---

## 공통: 안전 검사 (모든 모드에서 반드시 실행)

```bash
git status --short
git diff --stat
# 민감 파일명 검사
git diff --cached --name-only | grep -Ei '\.env$|\.env\.|secret|credential|password|private.key|id_rsa|\.pem$|token'
# 내용 중 개인정보 패턴 검사
git diff --cached | grep -E '^\+.*(password\s*=|api_key\s*=|secret\s*=|[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,})' | grep -v '^\+\+\+'
```

- 변경사항 없음 → "커밋할 변경사항이 없습니다" 안내 후 종료
- 민감 파일/내용 감지 → **해당 파일 제외 + 사용자에게 경고**, 나머지만 진행
- 충돌(conflict) 존재 → 파일 목록 표시 후 중단
- main/master 브랜치 → 어떤 모드든 한 번 확인

---

## 일반 모드

### 1. 스테이징

파일 목록을 보여주고 어떤 파일을 스테이징할지 확인한다.

```bash
git add -A          # 전체
git add <파일경로>  # 또는 특정 파일만
```

### 2. 커밋 메시지 제안

Conventional Commits 형식으로 초안을 제시하고 사용자가 수정하거나 그대로 사용할 수 있게 한다.

```
<type>(<scope>): <subject>
```

| type | 용도 |
|------|------|
| `feat` | 새 기능 |
| `fix` | 버그 수정 |
| `docs` | 문서 |
| `refactor` | 리팩토링 |
| `chore` | 설정·빌드 |

### 3. 커밋

```bash
git commit -m "$(cat <<'EOF'
<커밋 메시지>

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
git log --oneline -3
```

### 4. Push (선택)

**반드시 사용자에게 확인 후 진행한다.**

```
현재 브랜치: <브랜치명>
원격 저장소: <remote URL>
커밋: <커밋 메시지>

위 내용을 GitHub에 푸시할까요?
```

```bash
git push origin <브랜치명>
# 원격 브랜치 없는 경우
git push -u origin <브랜치명>
```

---

## 빠른 모드

### 1. 전체 스테이징 + 커밋 메시지 자동 결정

변경 파일 패턴으로 scope를 추론해 즉시 커밋한다. 사용자에게 묻지 않는다.

| 변경 경로 | scope |
|-----------|-------|
| `.claude/hooks/` | `hook` |
| `.claude/skills/` | `skill` |
| `.claude/settings.json` | `config` |
| `CLAUDE.md` | `claude` |
| `.gitignore` | `config` |

```bash
git add -A
git commit -m "$(cat <<'EOF'
<자동 결정한 커밋 메시지>

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

### 2. 결과 안내 (push 없음)

```
✅ 커밋 완료: <커밋 메시지>
브랜치: <브랜치명> | push는 수동으로 진행하세요.
```

---

## 안전 규칙 (모든 모드)

1. **force push 금지** — main/master에는 절대 거부
2. `--no-verify` 사용 금지
3. `.env`, `*secret*`, `*credential*` 파일 커밋 차단
4. push는 항상 사용자 확인 후 진행
