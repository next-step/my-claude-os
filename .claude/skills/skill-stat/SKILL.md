---
name: skill-stat
description: 사용자가 Claude를 호출한 횟수 통계를 보여준다. count-requests 훅이 기록한 .claude/request-count.txt 와 .claude/request-log.txt 를 분석한다. "호출 통계", "skill-stat", "몇 번 호출했어", "요청 횟수 보여줘" 등을 요청할 때 사용한다.
---

# /skill-stat — 호출 횟수 통계

`UserPromptSubmit` 훅(`.claude/hooks/count-requests.sh`)이 쌓아 둔 로그를 분석해
사용자가 Claude를 몇 번, 언제 호출했는지 통계로 보여준다.

## 데이터 출처
- `.claude/request-count.txt` — 누적 총 호출 수(숫자 한 줄)
- `.claude/request-log.txt` — `횟수<TAB>YYYY-MM-DD HH:MM:SS` 형식의 로그(한 줄 = 한 번의 호출)

## 절차

### 1. 데이터 존재 확인
- 두 파일이 없거나 비어 있으면, 아직 기록이 없다는 뜻이다.
  사용자에게 "아직 호출 기록이 없습니다. 훅이 적용된 새 세션에서 메시지를 보내면 쌓입니다."라고 안내하고 종료한다.
  (훅 설정은 세션 시작 시 로드되므로, 훅을 막 만든 세션에서는 기록이 없을 수 있다.)

### 2. 통계 집계 (아래 명령을 한 번에 실행)
```bash
LOG=".claude/request-log.txt"
echo "=== 총 호출 수 ==="
cat .claude/request-count.txt 2>/dev/null || echo 0

echo "=== 처음/마지막 호출 시각 ==="
head -n1 "$LOG" 2>/dev/null
tail -n1 "$LOG" 2>/dev/null

echo "=== 날짜별 호출 수 ==="
awk -F'\t' '{split($2,d," "); print d[1]}' "$LOG" 2>/dev/null | sort | uniq -c | sort -rn

echo "=== 시간대(시)별 호출 수 ==="
awk -F'\t' '{split($2,d," "); split(d[2],t,":"); print t[1]"시"}' "$LOG" 2>/dev/null | sort | uniq -c | sort -rn

echo "=== 오늘 호출 수 ==="
grep -c "$(date '+%Y-%m-%d')" "$LOG" 2>/dev/null || echo 0
```

### 3. 결과를 읽기 좋게 정리
- **총 호출 수**, **오늘 호출 수**, **활동 기간**(처음~마지막)을 먼저 요약한다.
- **날짜별** 분포를 간단한 표나 막대(`█` 반복)로 시각화한다.
- **가장 활발한 시간대**(피크 시)를 한 줄로 짚어준다.
- 한국어로, 한눈에 들어오게 보고한다.

## 참고
- 이 통계는 `count-requests` 훅이 기록한 데이터에 의존한다. 훅이 비활성화되어 있으면 숫자가 늘지 않는다.
- 기록을 초기화하려면 `.claude/request-count.txt` 와 `.claude/request-log.txt` 를 삭제하면 된다.
