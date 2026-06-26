---
name: sync-readme
description: 현재 프로젝트의 실제 상태(스킬·에이전트·디렉터리 구조)를 스캔해 README.md를 최신화한다.
user-invocable: true
allowed-tools: Read Glob Bash Agent
---

# /sync-readme 스킬 — README 최신화 오케스트레이터

`.claude/` 파일시스템의 **현재 실제 상태**를 스캔해서, 낡은 `README.md`를
실제 구현과 일치하도록 갱신한다.

스킬을 추가/삭제하거나 디렉터리 구조를 바꾼 뒤 실행하면, README의 스킬 표·
에이전트 표·디렉터리 트리·상태 흐름 설명을 한 번에 맞춰준다.

## 사용법

```
/sync-readme        ← 변경분을 반영한 README 갱신안을 만들고 보여준 뒤 적용
```

---

## 실행 절차

> **설계 포인트 — 정본(source of truth)을 읽는다**
> README는 사람이 손으로 쓰는 문서라 코드/스킬이 바뀌면 금세 낡는다.
> 이 스킬은 README의 *옛 내용을 믿지 않고*, 항상 실제 파일시스템을 스캔해
> 거기서 사실을 다시 길어 올린다. ([[remind-when]]이 crontab 정본을 직접
> 읽는 것과 같은 철학.)
>
> **오케스트레이터 패턴 포인트**
> 이 스킬은 "무엇을 어떤 순서로"만 정한다. ① 사실 수집(Bash/Read)과
> ② 실제 문서 작성(writer 에이전트 위임)을 분리한다.

### Step 1: 현재 프로젝트 상태 스캔 (사실 수집)

아래를 실행해 README가 반영해야 할 **실제 사실**을 모은다.

```bash
echo "=== 스킬 목록 + 각 description ===" && \
for f in .claude/skills/*/SKILL.md; do
  name=$(awk -F': ' '/^name:/{print $2; exit}' "$f")
  desc=$(awk -F': ' '/^description:/{print $2; exit}' "$f")
  printf -- "- /%s — %s\n" "$name" "$desc"
done && \
echo "" && echo "=== _shared / 로컬 에이전트 파일 ===" && \
ls -1 .claude/skills/_shared/*.md 2>/dev/null && \
find .claude/skills -name '_*.md' 2>/dev/null && \
echo "" && echo "=== 디렉터리 구조 ===" && \
( command -v tree >/dev/null && tree -a -I '.git' .claude || find .claude -not -path '*/.git/*' | sort )
```

> data/ 같은 비밀값 파일은 **존재 여부만** 확인하고, 토큰 등 내용은 README에 절대 옮기지 않는다.

### Step 2: 현재 README 읽기

Read `README.md` 를 읽어, Step 1의 사실과 **어긋나는 부분**을 식별한다.
주로 갱신 대상은: 스킬 표, "에이전트 종류" 표, "디렉터리 구조" 트리, 빠른 시작 예시,
상태 흐름 다이어그램.

### Step 3: state-sync-writer 공유 에이전트에 갱신 위임

`_shared/state-sync-writer` 공유 서브에이전트에 위임한다.
Agent 도구를 호출하며 아래 입력 계약을 채워 전달한다.

- **산출물 종류**: "README 문서"
- **대상 파일 경로**: `README.md`
- **스캔된 실제 사실**: Step 1 스캔 결과 전체
- **갱신해야 할 불일치/빈틈 목록**: Step 2에서 찾은 불일치 목록
- **도메인 특화 제약 (README 전용)**:
  - 기존 README의 **구성·말투·한국어 톤을 유지**한다 (전면 재작성 금지, 어긋난 부분만 고친다)
  - 스킬 표·에이전트 표·디렉터리 트리는 Step 1 사실 그대로 반영
  - 새로 추가된 스킬은 표와 디렉터리 트리 양쪽에 빠짐없이 등록
  - 사라진 스킬/파일은 문서에서 제거
  - 비밀값(토큰·ID 등)은 절대 본문에 쓰지 않는다

### Step 4: 결과 확인

갱신 후 `git diff README.md`로 변경분을 사용자에게 보여준다.
커밋은 사용자가 원할 때만 한다. (이 OS의 커밋은 사용자 결정 사항)

---

## 설계 노트 — 왜 이렇게 만들었나

- **AI 협업 학습 포인트**: "문서 최신화"는 두 가지 다른 일(사실 수집 + 글쓰기)의
  합성이다. 한 컨텍스트에서 다 하면 사실 확인이 흐려지므로, 수집은 결정적인
  Bash/Read로, 글쓰기는 전문 에이전트로 **역할을 쪼갰다**.
- README가 곧 이 레포의 "사용 설명서"이자 OS 설계의 거울이므로, 새 스킬을
  추가할 때마다 `/sync-readme` 한 번이면 문서가 따라오도록 한 것이 목적이다.
- **글쓰기는 공유 서브에이전트로**: 갱신 단계는 `_shared/state-sync-writer` 공유 서브에이전트로 추출해
  sync-readme와 sync-test([[sync-test]])가 재사용한다. ([[state-sync-writer]])
