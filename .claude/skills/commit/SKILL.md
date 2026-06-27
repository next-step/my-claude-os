---
name: commit
description: 현재 작업 내용을 분석해 의미 있는 단위로 git 커밋을 만들고 GitHub(원격)에 푸시한다. 사용자가 "커밋", "commit", "푸시", "push", "변경사항 올려줘" 등을 요청할 때 사용한다.
---

# /commit — 커밋 후 GitHub 푸시

작업한 변경사항을 좋은 커밋 메시지와 함께 커밋하고, 원격 저장소(GitHub)에 푸시한다.

## 절차

### 1. 현재 상태 파악 (반드시 병렬로 한 번에 실행)
다음 명령을 **한 메시지에서 동시에** 실행해 전체 맥락을 본다:
- `git status` — 변경/추적되지 않은 파일 확인
- `git diff HEAD` — 스테이징 여부와 무관하게 실제 변경 내용 확인
- `git log --oneline -10` — 이 저장소의 커밋 메시지 스타일(언어·형식·접두어) 파악
- `git branch --show-current` — 현재 브랜치 확인

### 2. 변경 내용 분석
- diff를 읽고 **무엇이 왜 바뀌었는지** 한 문장으로 요약할 수 있어야 한다.
- 서로 무관한 변경이 섞여 있으면 사용자에게 "나눠서 커밋할지" 물어본다.
- `.env`, 비밀키, 대용량 파일, 빌드 산출물 등 **커밋하면 안 되는 파일**이 보이면 중단하고 사용자에게 알린다.

### 3. 스테이징
- 사용자가 특정 파일만 지정했으면 그 파일만 `git add`.
- 별도 지정이 없으면 관련 변경을 `git add`로 스테이징한다. (`git add -A`는 의도치 않은 파일을 포함할 수 있으니 status를 확인한 뒤 사용)

### 4. 커밋 메시지 작성
- **3번 단계에서 본 기존 커밋 스타일을 따른다.** (이 저장소는 한국어 메시지를 사용)
- 형식: 제목 한 줄(명령형, 50자 내외) + 필요 시 빈 줄 후 본문.
- "무엇을"이 아니라 **"왜"**가 드러나게 쓴다.
- 메시지 끝에 아래 서명을 포함한다:

```
🤖 Generated with [Claude Code](https://claude.com/claude-code)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
```

HEREDOC를 사용해 형식을 안전하게 전달한다:
```bash
git commit -m "$(cat <<'EOF'
<제목>

<본문(선택)>

🤖 Generated with [Claude Code](https://claude.com/claude-code)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

### 5. GitHub로 푸시
- `git push` 실행.
- 현재 브랜치에 업스트림이 없으면 `git push -u origin <현재-브랜치>`로 설정한다.
- 푸시가 거부되면(remote가 앞서 있음) 사용자에게 알리고, 임의로 `--force` 하지 않는다.

### 6. 결과 보고
- 커밋 해시와 메시지 제목, 푸시된 브랜치를 한 줄로 보고한다.
- 가능하면 GitHub의 커밋/브랜치 비교 URL을 안내한다.

## 안전 수칙
- **사용자가 명시적으로 요청하지 않는 한** `git push --force`, `git reset --hard`, 히스토리 재작성(`rebase`, `commit --amend` of 푸시된 커밋)을 하지 않는다.
- 메인 브랜치(`main`/`master`)에 직접 푸시하기 전에는, 보호 정책이 있을 수 있으니 한 번 더 확인한다.
- 커밋할 변경이 없으면(`nothing to commit`) 그 사실을 알리고 종료한다.
- 회사계정으로 커밋하지 않도록 개인계정으로 변경 후 작업을 진행한다. 해당 명령어는 아래와 같다.
  - git remote set-url origin https://ChoiJeongHyun@github.com/ChoiJeongHyun/my-claude-code-os.git
