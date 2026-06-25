---
name: github-commit
description: |
  현재 프로젝트의 변경사항을 GitHub에 커밋하고 푸시하는 스킬.
  git add → commit → push 전체 과정을 안전하게 자동화한다.
  
  다음 상황에서 반드시 사용한다:
  - "커밋해줘", "깃허브에 올려줘", "push해줘", "변경사항 저장해줘"
  - "git commit", "git push" 관련 요청
  - 작업 완료 후 "저장소에 반영해줘" 같은 표현
  - 파일 수정 후 버전 관리가 필요한 상황
---

# GitHub Commit 스킬

변경사항을 안전하게 GitHub에 커밋하고 푸시하는 워크플로우.

---

## 워크플로우

### 1단계: 현재 상태 파악

아래 명령을 병렬로 실행해서 현재 상태를 한눈에 파악한다.

```bash
git status          # 변경/추가/삭제된 파일 목록
git diff --stat     # 변경된 내용 요약
git log --oneline -5  # 최근 커밋 히스토리 (메시지 스타일 파악용)
```

**확인할 것:**
- 커밋할 변경사항이 있는가?
- 현재 브랜치가 의도한 브랜치인가?
- 민감한 정보(API 키, 비밀번호, `.env`)가 포함되지 않았는가?

변경사항이 없으면 사용자에게 알리고 중단한다.

---

### 2단계: 파일 스테이징 (git add)

사용자가 특정 파일을 지정하지 않았다면, 변경된 파일 목록을 보여주고 어떤 파일을 스테이징할지 확인한다.

```bash
# 전체 스테이징
git add -A

# 또는 특정 파일만
git add <파일경로>
```

**주의:** `.env`, `credentials.*`, `*secret*` 같은 민감한 파일이 있으면 반드시 경고하고 사용자 확인을 받아야 한다.

---

### 3단계: 커밋 메시지 작성

#### Conventional Commits 형식을 따른다

```
<type>(<scope>): <subject>

[optional body]
```

**type 종류:**
| type | 설명 | 예시 |
|------|------|------|
| `feat` | 새로운 기능 추가 | `feat(auth): 로그인 기능 추가` |
| `fix` | 버그 수정 | `fix(api): null 응답 처리 수정` |
| `docs` | 문서 수정 | `docs: README 업데이트` |
| `style` | 코드 포맷 변경 (기능 변경 없음) | `style: 들여쓰기 정리` |
| `refactor` | 리팩토링 | `refactor(user): 서비스 레이어 분리` |
| `test` | 테스트 추가/수정 | `test: 로그인 단위 테스트 추가` |
| `chore` | 빌드, 설정 변경 | `chore: .gitignore 업데이트` |
| `perf` | 성능 개선 | `perf: 쿼리 N+1 문제 해결` |

#### 커밋 메시지 작성 원칙
- subject는 50자 이내
- 현재형으로 작성 (추가했다 → 추가, add/added → add)
- 변경 이유가 명확하지 않으면 body에 설명 추가

커밋 메시지 초안을 제시하고, 사용자가 수정하거나 그대로 사용할 수 있게 한다.

---

### 4단계: 커밋 실행

```bash
git commit -m "$(cat <<'EOF'
<작성된 커밋 메시지>

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

커밋 후 결과를 확인한다:
```bash
git log --oneline -3
```

---

### 5단계: 푸시 (git push)

**푸시 전에 반드시 사용자에게 확인을 받는다.**

```
현재 브랜치: <브랜치명>
원격 저장소: <remote URL>
커밋: <커밋 메시지>

위 내용을 GitHub에 푸시할까요?
```

확인 후 실행:
```bash
git push origin <브랜치명>
```

원격 브랜치가 없는 경우:
```bash
git push -u origin <브랜치명>
```

---

## 안전 규칙

1. **절대 force push 하지 않는다** — 사용자가 명시적으로 요청해도 main/master 브랜치에는 거부한다
2. **민감한 파일 커밋 차단** — `.env`, `*secret*`, `*credential*` 패턴 파일 발견 시 경고
3. **훅 우회 금지** — `--no-verify` 사용 금지
4. **푸시 전 확인 필수** — 자동으로 push하지 않고 항상 사용자 확인 후 진행

---

## 예외 처리

| 상황 | 대응 |
|------|------|
| 변경사항 없음 | "커밋할 변경사항이 없습니다" 안내 후 종료 |
| 충돌(conflict) 있음 | 충돌 파일 목록 표시, 해결 방법 안내 후 중단 |
| 원격 브랜치와 다이버전스 | `git pull --rebase` 먼저 실행 제안 |
| pre-commit 훅 실패 | 훅 실패 원인 분석, 수정 후 재시도 |
| 인증 오류 | SSH 키 또는 토큰 설정 방법 안내 |

---

## 사용 예시

**사용자:** "지금까지 수정한 거 커밋해줘"

**Claude가 할 일:**
1. `git status`, `git diff --stat`, `git log --oneline -5` 실행
2. 변경 파일 목록과 내용 요약 표시
3. 커밋 메시지 초안 제안 (예: `feat(skill): github-commit 스킬 추가`)
4. 사용자 확인 후 `git add -A && git commit`
5. 푸시 여부 확인 후 `git push`
