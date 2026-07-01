---
name: pr
description: 현재 브랜치의 커밋들을 분석해 "무엇이 왜 바뀌었는지"를 LLM이 종합한 PR 제목·본문을 만들고, 사람 확인 후 gh로 PR을 연다. OS.md 흐름의 ④ 마무리(/commit·/push 다음). 본문은 .github/pull_request_template.md 틀을 따른다(PreToolUse 훅이 강제). 사용자가 "PR 올려줘", "PR 만들어줘", "리뷰 요청", "/pr"이라고 할 때 사용한다.
user-invocable: true
allowed-tools:
  - Read
  - Bash(git:*)
  - Bash(gh:*)
  - Task
---

# /pr — 커밋 분석 → PR 생성 (④ 마무리)

푸시된 브랜치의 **커밋들을 모아 LLM이 PR 제목·본문을 종합 생성**하고, **사람 확인 후** `gh`로 PR을 연다.
본문은 [.github/pull_request_template.md](../../../.github/pull_request_template.md)의 틀을 따른다
(`## 요약` / `## 변경 내용` / `## 테스트` / `## 관련`). 이 틀은 **PreToolUse 훅이 강제**하므로,
섹션이 빠지면 `gh pr create`가 차단된다.

> 외부 공개 작업이다. 되돌리기 어려우니 **사람 확인 전에는 PR을 만들지 않는다.**

전달된 인자: ``
- 있으면 → PR 제목 힌트/강조점으로 사용. 없으면 커밋들에서 알아서 종합.

---

## 진행 순서

### [준비] 사전 점검 (하나라도 막히면 멈추고 안내)

```bash
# 대상 저장소: 내 fork(origin). gh는 remote가 여럿이면 upstream을 고르므로 --repo 로 강제한다.
repo=$(git remote get-url origin | sed -E 's#\.git$##; s#^.*[:/]([^/]+/[^/]+)$#\1#')
# base 브랜치: 설정값(nextstep.prbase) 우선 → origin 기본 브랜치 → main
base=$(git config --get nextstep.prbase 2>/dev/null || true)
[ -z "$base" ] && base=$(git symbolic-ref refs/remotes/origin/HEAD 2>/dev/null | sed 's#.*/##')
base=${base:-main}
br=$(git branch --show-current)
```

> **fork·base 설정** — PR을 보낼 저장소·브랜치는 위처럼 결정한다. base 브랜치를 바꾸려면
> `git config nextstep.prbase <브랜치>` (예: 내 id 브랜치 `jshans40`). 대상 저장소는 항상 `origin`(내 fork)이며
> `--repo "$repo"` 로 박아 upstream으로 새지 않게 한다.

- **현재 브랜치가 base면** → "PR은 기능 브랜치에서 올리세요"라고 안내하고 멈춘다.
- **앞선 커밋이 없으면**(`git log $base..HEAD --oneline` 비어 있음) → "올릴 커밋이 없습니다" 멈춤.
- **원격에 안 올라갔거나 뒤처져 있으면**(로컬 HEAD가 `origin/$br`에 없음) → "먼저 `/push` 하세요" 안내.
- **gh 인증 안 됨**(`gh auth status` 실패) → 인증 안내하고 멈춤.
- **이미 열린 PR이 있으면**(`gh pr view --repo "$repo" "$br" --json url -q .url` 성공) → 그 URL 알리고 **새로 안 만든다**(중복 금지).

### 1. 변경 수집

```bash
git log $base..HEAD --oneline          # 커밋 목록
git diff $base...HEAD --stat            # 파일별 변경 규모
```
필요하면 핵심 파일의 `git diff $base...HEAD -- <경로>`로 내용을 본다.
**diff가 매우 크면** `Task`로 분석 서브에이전트에 위임해 요약만 받는다(컨텍스트 격리).

### 2. PR 제목·본문 생성 (LLM = 메인)

- `.github/pull_request_template.md`를 `Read`해서 **그 틀에 맞춰** 채운다.
- **제목**: 핵심을 한 줄로(기존 커밋 접두사 스타일 참고: `feat :` 등).
- **본문**: 커밋 메시지·diffstat을 근거로 **"무엇이 왜 바뀌었나"를 종합**한다. *커밋 단순 나열 금지.*
  - `## 요약` 1~3줄 / `## 개발 흐름` **템플릿의 mermaid 블록을 그대로 유지(항상 포함, 훅이 강제 — GitHub가 다이어그램으로 렌더)** / `## 변경 내용` 주요 항목 / `## 테스트` 실제 검증 결과(예: pytest N/M, 모르면 "확인 필요") / `## 관련` 링크(선택).

### 3. 사람 확인 게이트 — ★멈춤★

생성한 **제목·본문 전체를 보여주고** PR을 만들지 확인한다.

```
[PR 미리보기]
제목: ...
본문:
## 요약 ...
## 변경 내용 ...
## 테스트 ...

이대로 PR을 만들까요? (base: <base> ← <branch>) 고칠 점 있으면 알려주세요.
```

- 수정 요청하면 다듬어 다시 보여준다. **승인 전에는 `gh pr create`를 실행하지 않는다.**

### 4. PR 생성

승인되면 본문을 **인라인 `--body`로** 넘겨 만든다(훅이 틀을 검사할 수 있게 — `--body-file` 말고 `--body`).

```bash
gh pr create --repo "$repo" --base "$base" --head "$br" --title "<제목>" --body "<틀 채운 본문>"
```

- 훅이 본문에 `## 요약`/`## 변경 내용`/`## 테스트`가 있는지 검사한다. 빠지면 차단되니, 틀을 지켜 작성한다.
- 생성된 **PR URL을 보고**한다.

---

## 수용 기준 (이 스킬이 제대로 동작하는지 검증)

- [ ] base가 아닌 기능 브랜치에서만, 푸시된 상태에서만 진행한다.
- [ ] 이미 열린 PR이 있으면 새로 만들지 않고 알린다.
- [ ] 커밋들을 근거로 **종합한** 제목·본문을 만든다(단순 나열 아님).
- [ ] 본문이 템플릿 틀(## 요약/## 변경 내용/## 테스트)을 따른다.
- [ ] 사람 확인 게이트에서 멈추고, 승인 없이 PR을 만들지 않는다.
- [ ] 생성 후 PR URL을 보고한다.

---

## 안전 규칙

- **외부 공개 작업** — 사람 확인 없이 PR을 만들지 않는다.
- **base→base PR 금지, 중복 PR 금지.**
- 본문은 **반드시 템플릿 틀**을 따른다(훅이 강제).
- diff에 **비밀·토큰·`.env` 같은 민감정보**가 보이면 멈추고 알린다.
- `--force`나 PR close/merge 같은 동작은 이 스킬이 하지 않는다.
