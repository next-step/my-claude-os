---
name: skill-stat
description: 훅으로 기록된 스킬 호출 횟수 통계를 보여준다. 사용자가 "스킬 통계", "skill-stat", "어떤 스킬 많이 썼어?" 등을 물을 때 사용한다.
---

# skill-stat

`.claude/hooks/record-skill-usage.py` 훅이 `.claude/skill-stats.json` 에 누적한
스킬 호출 횟수를 읽어 사람이 보기 좋은 통계로 정리한다.

## 절차

1. **통계 파일 읽기** — `$CLAUDE_PROJECT_DIR/.claude/skill-stats.json` 를 읽는다.
   (없으면 아직 기록된 호출이 없는 것이므로 그 사실을 알린다.)

2. **집계** — 다음을 계산한다.
   - 전체 호출 수(`total`)
   - 스킬별 호출 수(`count`), 호출 수 내림차순 정렬
   - 각 스킬의 마지막 사용 시각(`last_used`)

3. **표로 출력** — 다음 형식으로 보여준다.

   ```
   📊 스킬 호출 통계 (총 N회)

   | 순위 | 스킬        | 호출 수 | 비율  | 마지막 사용        |
   |------|-------------|---------|-------|--------------------|
   | 1    | commit      | 12      | 40.0% | 2026-06-25 20:30   |
   | 2    | skill-stat  | 8       | 26.7% | 2026-06-25 20:31   |
   ...
   ```

   비율은 `count / total * 100` 으로 계산한다.

## 구현 메모

- 빠르게 보여주려면 한 번에 읽고 정렬해 출력하면 된다. 예시 명령:
  ```bash
  python3 - "$CLAUDE_PROJECT_DIR/.claude/skill-stats.json" <<'PY'
  import json, sys
  data = json.load(open(sys.argv[1], encoding="utf-8"))
  total = data.get("total", 0)
  rows = sorted(data.get("skills", {}).items(), key=lambda kv: kv[1]["count"], reverse=True)
  print(f"📊 스킬 호출 통계 (총 {total}회)\n")
  print(f"{'순위':<4} {'스킬':<14} {'호출':>5} {'비율':>7}  마지막 사용")
  for i, (name, s) in enumerate(rows, 1):
      pct = (s['count'] / total * 100) if total else 0
      print(f"{i:<4} {name:<14} {s['count']:>5} {pct:>6.1f}%  {s.get('last_used','-')}")
  PY
  ```
- `$CLAUDE_PROJECT_DIR` 가 비어 있으면 현재 작업 디렉터리 기준으로 `.claude/skill-stats.json` 를 찾는다.
