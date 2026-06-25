---
name: commit
description: 변경사항을 분석해 의미 있는 커밋 메시지로 git 커밋하고 GitHub에 푸시한다. 사용자가 "커밋해줘", "깃헙에 올려줘", "푸시해줘"라고 요청할 때 사용한다.
user-invocable: true
allowed-tools:
  - Bash(git status:*)
  - Bash(git diff:*)
  - Bash(git log:*)
  - Bash(git add:*)
  - Bash(git commit:*)
  - Bash(git push:*)
  - Bash(git branch:*)
  - Bash(git rev-parse:*)
---

# /commit — GitHub 커밋 & 푸시 스킬

변경된 코드를 분석하고, 좋은 커밋 메시지를 작성해서 git 커밋한 뒤 GitHub로 푸시한다.

전달된 인자: `$ARGUMENTS`
- 인자가 있으면 커밋 메시지의 힌트/방향으로 사용한다 (예: `/commit 로그인 버그 수정`).
- 인자가 없으면 변경 내용을 보고 메시지를 직접 작성한다.

---

## 진행 순서

### 1. 현재 상태 파악 (병렬 실행)
다음 명령을 한 번에 실행해 무엇이 바뀌었는지 전체 그림을 본다.

```
git status
git diff --staged      # 이미 스테이징된 변경
git diff               # 아직 스테이징 안 된 변경
git log --oneline -5   # 최근 커밋 스타일 참고
```

- 변경사항이 전혀 없으면 여기서 멈추고 사용자에게 알린다.
- `git log`로 이 저장소의 커밋 메시지 컨벤션(언어, 형식, prefix 등)을 파악해 맞춘다.

### 2. 스테이징
- 사용자가 특정 파일만 지정하지 않았다면 관련된 변경을 `git add`로 스테이징한다.
- 무관한 변경이 섞여 있다고 판단되면 사용자에게 어떤 것을 커밋할지 확인한다.
- `.env`, 비밀키, 대용량 파일 등 커밋하면 안 될 파일이 보이면 **반드시 먼저 경고**한다.

### 3. 커밋 메시지 작성
- "무엇을" 바꿨는지가 아니라 "왜" 바꿨는지가 드러나게 쓴다.
- 저장소의 기존 컨벤션을 따른다 (이 저장소 최근 커밋은 한국어 사용).
- 제목은 간결하게 한 줄. 필요하면 본문에 상세 설명을 추가한다.
- 커밋 메시지 끝에 아래 서명을 포함한다:

```
Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
```

### 4. 커밋 실행
HEREDOC을 사용해 메시지 형식이 깨지지 않도록 한다.

```
git commit -m "$(cat <<'EOF'
<커밋 제목>

<본문 (선택)>

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

### 5. GitHub로 푸시
- 현재 브랜치를 확인한다: `git rev-parse --abbrev-ref HEAD`
- **`main` 브랜치에 직접 푸시하기 전에는 사용자에게 확인**받는다. 기능 브랜치면 바로 푸시해도 좋다.
- `git push`. 업스트림이 없으면 `git push -u origin <브랜치명>`으로 설정한다.

### 6. 결과 보고
- 커밋 해시, 커밋 메시지, 푸시된 브랜치를 요약해 사용자에게 보여준다.
- 푸시가 거부되면(예: 원격이 앞서 있음) 원인과 해결책(`git pull --rebase` 등)을 안내한다.

---

## 안전 규칙
- 커밋/푸시는 되돌리기 번거로운 작업이다. 의심스러우면 실행 전에 물어본다.
- `git push --force`는 사용자가 명시적으로 요청하지 않는 한 절대 쓰지 않는다.
- 비밀정보가 포함된 파일은 스테이징하지 않고 경고한다.
