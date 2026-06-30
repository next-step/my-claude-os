---
name: skill-stat
description: 스킬 호출 기록 로그(.claude/skill-usage.log)를 읽어 각 스킬별 호출 횟수와 총 호출 수, 최근 호출 이력을 집계해 보여준다. 사용자가 "스킬 통계", "어떤 스킬 많이 썼어", "skill-stat" 등을 요청할 때 사용한다.
---

# skill-stat — 스킬 사용 통계

`PreToolUse` 훅이 쌓아둔 `.claude/skill-usage.log`(`타임스탬프\t스킬이름` 형식)를
읽어 사용 통계를 보여준다.

## 실행 절차

### 1단계 — 로그 존재 확인
```bash
test -f "$CLAUDE_PROJECT_DIR/.claude/skill-usage.log" || echo "아직 기록된 스킬 호출이 없습니다."
```
- 파일이 없으면 "기록 없음"을 안내하고, 훅이 활성화돼 있는지(`/hooks` 또는 재시작 필요) 짚어준다.
- `$CLAUDE_PROJECT_DIR`가 없으면 프로젝트 루트의 `.claude/skill-usage.log`를 사용한다.

### 2단계 — 집계
```bash
LOG="$CLAUDE_PROJECT_DIR/.claude/skill-usage.log"
echo "총 호출: $(wc -l < "$LOG" | tr -d ' ')회"
echo "--- 스킬별 호출 횟수 (많은 순) ---"
cut -f2 "$LOG" | sort | uniq -c | sort -rn
echo "--- 최근 호출 5건 ---"
tail -n 5 "$LOG"
```

### 3단계 — 보고
결과를 표 형태로 보기 좋게 정리해 전달한다. 예:

| 스킬 | 호출 횟수 |
|------|-----------|
| commit | 3 |
| skill-stat | 1 |

총 N회 호출, 가장 많이 쓴 스킬과 최근 사용 흐름을 한두 줄로 요약한다.

## 참고
- 이 통계는 `PreToolUse`(matcher: `Skill`) 훅이 켜져 있어야 쌓인다. 훅을 켠 세션부터 기록되므로,
  그 이전 호출은 집계에 없다.
- 로그는 `.gitignore` 대상(런타임 생성물)이라 사람마다·머신마다 따로 쌓인다.
