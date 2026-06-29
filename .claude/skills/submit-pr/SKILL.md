---
name: submit-pr
description: |
  현재 브랜치를 next-step/my-claude-code-os의 minnseong 브랜치로 PR을 생성하는 스킬.

  다음 표현에 반드시 사용한다:
  - "/submit-pr title="day1" mission="미션 내용""
  - "PR 올려줘 title=... mission=..."
  - "제출해줘 title=... mission=..."
  - "PR 생성해줘"
---

# Submit PR 스킬

현재 브랜치의 커밋 이력과 diff를 분석해 PR description을 자동 생성하고,
`next-step/my-claude-code-os`의 `minnseong` 브랜치로 PR을 생성한다.

---

## 인자 파싱

사용자 입력에서 아래 두 인자를 추출한다.

| 인자 | 설명 | 예시 |
|------|------|------|
| `title` | PR 제목으로 사용 | `day1`, `week2` |
| `mission` | 달성 여부를 평가할 미션 내용 | `"서브에이전트 3개 이상 구현"` |

파싱 방법: 사용자 입력에서 `title="..."` 또는 `title=...` 패턴으로 추출.
인자가 없으면 사용자에게 두 인자를 입력하도록 요청하고 중단한다.

---

## Step 1: 사전 조건 확인

### 1-1. gh CLI 확인

```bash
which gh 2>/dev/null || echo "NOT_FOUND"
```

- `NOT_FOUND`이면 아래 안내 후 **brew 설치를 제안**하고 중단:
  ```
  ⚠️  gh CLI가 설치되지 않았습니다.
  설치: brew install gh
  설치 후 인증: gh auth login
  설치 완료 후 다시 "/submit-pr title=... mission=..." 를 실행하세요.
  ```

### 1-2. gh 인증 확인

```bash
gh auth status 2>&1
```

- 인증되지 않은 경우 → `gh auth login` 실행 안내 후 중단

### 1-3. 현재 브랜치 및 리모트 확인

```bash
git branch --show-current
git remote -v
git status --short
```

- 커밋되지 않은 변경사항이 있으면 → "먼저 변경사항을 커밋해주세요 (`/github-commit`)" 안내

---

## Step 2: 커밋 이력 및 Diff 분석

아래 명령을 모두 실행해 PR description 작성에 필요한 정보를 수집한다.

```bash
# 현재 브랜치가 minnseong 브랜치에서 갈라진 이후의 커밋 목록
git log origin/minnseong..HEAD --oneline

# 커밋 상세 (author, date, message)
git log origin/minnseong..HEAD --pretty=format:"%h %ad %s" --date=short

# 변경된 파일 목록
git diff origin/minnseong..HEAD --name-only

# 변경 규모 요약
git diff origin/minnseong..HEAD --stat

# 주요 변경 내용 (diff 전체 — 너무 길면 --stat으로 대체)
git diff origin/minnseong..HEAD
```

> origin/minnseong이 없으면 `main`을 기준으로 대체한다.

---

## Step 3: PR Description 작성

수집한 정보를 바탕으로 아래 형식의 PR description을 작성한다.

```markdown
## 개요

[현재 브랜치에서 달성한 핵심 내용을 2~3문장으로 요약]

---

## 주요 변경사항

### 생성/추가된 것
- [파일/기능별로 bullet 정리]

### 수정된 것
- [파일/기능별로 bullet 정리]

---

## 커밋 이력

| 커밋 | 날짜 | 내용 |
|------|------|------|
| [hash] | [date] | [message] |
...

---

## 미션 달성 여부

**미션:** [인자로 받은 mission 내용]

### 달성 항목
- [x] [달성된 항목 — 커밋/코드 근거 포함]

### 미달성 항목
- [ ] [미달성 항목 — 이유 또는 부분 달성 여부 설명]

### 종합 평가
[전체적인 미션 달성 수준을 1~2문장으로 평가]

---

🤖 Generated with [Claude Code](https://claude.ai/claude-code)
```

description 초안을 사용자에게 보여준 뒤, **사용자가 수정 요청을 하면 반영**하고 확인을 받은 후 Step 4로 넘어간다.

---

## Step 4: PR 생성

사용자가 description을 확인/수정하면 PR을 생성한다.

```bash
gh pr create \
  --repo next-step/my-claude-code-os \
  --base minnseong \
  --head minnseong:{현재 브랜치명} \
  --title "{title 인자}" \
  --body "$(cat <<'EOF'
{Step 3에서 작성한 description}
EOF
)"
```

> `--head` 형식: `{fork owner}:{branch}` — fork 저장소에서 PR을 올릴 때 필요하다.
> fork owner는 `git remote -v`에서 `origin` URL의 owner(`minnseong`)를 사용한다.

---

## Step 5: 결과 보고

```
✅ PR 생성 완료!

제목: {title}
대상: next-step/my-claude-code-os ← minnseong/{현재 브랜치명}
URL: {gh pr create 결과로 나온 URL}

─────────────────────────────
PR을 열어서 확인하고 필요하면 수정하세요.
수정이 필요한 경우:
  - GitHub 웹에서 직접 편집 가능
  - 또는 "PR 설명 수정해줘" 로 요청하면 gh pr edit 으로 반영
─────────────────────────────
```

---

## 실행 원칙

- Step 3의 description 초안은 반드시 사용자에게 보여주고 **확인을 받은 뒤** PR을 생성한다.
- 미션 달성 여부는 실제 코드/커밋 근거를 기반으로 판단하며, 추측으로 체크하지 않는다.
- `--force` 옵션이나 기존 PR 덮어쓰기는 사용하지 않는다. 이미 PR이 있으면 URL을 안내한다.
- PR 생성 후 추가 수정 요청이 오면 `gh pr edit {PR번호} --body "..."` 로 반영한다.
