---
name: skill-stat
description: 스킬 호출 통계를 보여준다. 사용자가 "스킬 통계", "어떤 스킬 많이 썼어?", "skill-stat", "스킬 사용 현황", "호출 횟수 보여줘" 등을 요청할 때 사용한다. hook이 기록한 .claude/logs/skill-usage.log를 분석해 스킬별 호출 횟수와 최근 사용 내역을 정리한다.
---

# 스킬 사용 통계 (skill-stat)

`.claude/logs/skill-usage.log`에 쌓인 스킬 호출 기록을 분석해 통계로 보여준다.
이 로그는 `skill-counter` hook이 스킬이 실행될 때마다 "시각<TAB>스킬이름" 형식으로 한 줄씩 남긴다.

## 사용 시점
- "스킬 통계 보여줘", "어떤 스킬 제일 많이 썼어?", "스킬 사용 현황", "호출 횟수" 같은 요청을 받았을 때

## 절차

### 1. 로그 파일 확인
- 경로: `.claude/logs/skill-usage.log`
- 파일이 없거나 비어 있으면, 아직 기록이 없다고 안내한다.
  (hook은 다음 세션부터 자동 적용되므로, 갓 설정한 직후라면 기록이 없을 수 있다고 덧붙인다.)

### 2. 통계 집계
아래 명령으로 한 번에 집계한다. (탭 구분: 1번째 열=시각, 2번째 열=스킬이름)

```bash
LOG=.claude/logs/skill-usage.log
echo "총 호출 횟수: $(wc -l < "$LOG" | tr -d ' ')"
echo "사용한 스킬 종류: $(cut -f2 "$LOG" | sort -u | wc -l | tr -d ' ')"
echo "--- 스킬별 호출 횟수 (많은 순) ---"
awk -F'\t' '{c[$2]++} END {for (s in c) printf "%6d  %s\n", c[s], s}' "$LOG" | sort -rn
echo "--- 최근 호출 5건 ---"
tail -n 5 "$LOG"
```

### 3. 결과 정리해서 보여주기
- 위 출력을 사람이 읽기 좋은 표 형태로 정리한다. 예:

  | 스킬 | 호출 횟수 | 비율 |
  |------|----------:|-----:|
  | git-commit | 12 | 60% |
  | deep-research | 5 | 25% |
  | skill-stat | 3 | 15% |

- 가장 많이 쓴 스킬, 총 호출 횟수, 사용한 스킬 종류 수를 한 줄 요약으로 덧붙인다.
- 최근 호출 몇 건(시각 포함)도 함께 보여주면 사용 흐름을 파악하기 좋다.

## 옵션 (사용자가 요청하면)
- **특정 스킬만**: `grep -c "<TAB>스킬이름$"` 또는 `grep "스킬이름" "$LOG"` 로 해당 스킬만 집계/조회
- **날짜별 추이**: `cut -f1 "$LOG" | cut -d' ' -f1 | sort | uniq -c` 로 날짜별 호출 수
- **기록 초기화**: 사용자가 명시적으로 원하면 `: > .claude/logs/skill-usage.log` 로 비운다. (되돌릴 수 없으니 확인 후 실행)

## 파이썬 대안 (scripts/skill_stats.py)
위 bash 집계와 동일한 로그를 파이썬으로 집계하는 스크립트가 있다. 터미널에서 직접 돌리거나 결과를 다른 도구에 넘길 때 쓴다. (대화 중 자연어 요청은 이 스킬로, 직접 실행은 스크립트로.)

```bash
# 기본 로그 경로(.claude/logs/skill-usage.log)를 집계
python3 scripts/skill_stats.py

# 다른 로그 파일을 지정
python3 scripts/skill_stats.py path/to/other.log

# 가장 많이 쓴 상위 N개 스킬만 보기
python3 scripts/skill_stats.py --top 3

# 위치 인자(로그 경로)와 함께 쓸 수도 있다
python3 scripts/skill_stats.py path/to/other.log --top 3
```

출력 예시:

```
총 호출 수: 4
스킬 종류 수: 3
--- 스킬별 호출 횟수 (많은 순) ---
     2  git-commit
     1  deep-research
     1  feature-dev
```

`--top 2`를 준 경우 (요약은 전체 기준, 목록만 상위 2개로 잘림):

```
총 호출 수: 4
스킬 종류 수: 3
--- 스킬별 호출 횟수 (많은 순) 상위 2 ---
     2  git-commit
     1  deep-research
```

- 스킬별 횟수는 호출 수 내림차순, 동수면 이름 오름차순으로 정렬한다.
- `--top N`은 스킬별 목록만 상위 N개로 자른다. 상단 요약(총 호출 수/스킬 종류 수)은 항상 전체 기준으로 표시된다.
- `--top`을 생략하면 기존처럼 전체를 출력한다. `N`이 전체 종류 수보다 크면 전체가 나온다.
- `--top 0` 또는 음수를 주면 목록이 `(기록 없음)`으로 표시된다. `--top abc`처럼 비정수를 주면 argparse가 거부하며 종료 코드 `2`로 끝난다.
- 로그 파일이 없으면 안내 후 종료 코드 `1`로 끝난다.
- 핵심 로직은 순수 함수 `count_skills(lines) → {스킬이름: 횟수}`이고, 정렬/상위 N개 추출은 `top_skills(counts, n)`에 모여 있다. 표준 라이브러리만 쓴다.
- 테스트: `python3 -m unittest discover tests` (`tests/test_skill_stats.py`, 16개 케이스 = 집계 8 + `--top` 8)

## 주의사항
- 이 스킬 자체도 `Skill` 툴로 실행되므로, hook에 의해 `skill-stat` 호출도 로그에 기록된다. (통계에 자기 자신이 포함될 수 있음을 인지한다.)
- 로그 형식이 "시각<TAB>스킬이름"임을 전제로 한다. hook 스크립트(`.claude/hooks/skill-counter.sh`)를 바꾸면 이 집계 명령도 맞춰 수정해야 한다.
