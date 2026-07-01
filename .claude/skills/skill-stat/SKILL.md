---
name: skill-stat
description: 지금까지 호출된 skill들의 사용 통계(호출 횟수, 마지막 호출 시각)를 보여준다. 사용자가 "skill 통계", "스킬 사용 통계", "skill-stat", "어떤 스킬을 많이 썼는지" 등을 물을 때 사용.
---

# Skill 사용 통계

`~/.claude/hooks/skill-usage-log.sh` PreToolUse hook가 skill을 호출할 때마다
`~/.claude/skill-stats.json`에 누적한 데이터를 읽어 통계로 보여준다.

데이터 형식:
```json
{
  "commit":     { "count": 3, "last": "2026-06-25T11:23:11Z" },
  "skill-stat": { "count": 1, "last": "2026-06-25T11:25:02Z" }
}
```

## 절차

1. **통계 데이터 읽기** — 아래 명령으로 호출 횟수 내림차순 정렬된 표 형태의 데이터를 얻는다.

   ```bash
   STATS="$HOME/.claude/skill-stats.json"
   if [ ! -s "$STATS" ] || [ "$(jq 'length' "$STATS" 2>/dev/null)" = "0" ]; then
     echo "아직 기록된 skill 호출이 없습니다."
   else
     jq -r '
       to_entries
       | sort_by(-.value.count)
       | (map(.value.count) | add) as $total
       | "총 호출 횟수: \($total)\n",
         "순위  스킬                  횟수   마지막 호출",
         "----  --------------------  -----  --------------------",
         (to_entries[] |
           "\(.key + 1 | tostring | (" " * (4 - length)) + .)  "
           + (.value.key | . + (" " * (20 - (.|length))))[0:20] + "  "
           + (.value.value.count | tostring | (" " * (5 - length)) + .) + "  "
           + (.value.value.last // "-"))
     ' "$STATS"
   fi
   ```

   > 위 jq의 정렬 결과를 그대로 쓰되, 정렬/포맷이 까다로우면 `jq -r 'to_entries | sort_by(-.value.count)[] | "\(.key): \(.value.count)회 (마지막 \(.value.last))"' "$STATS"` 처럼 단순하게 뽑아도 된다.

2. **사람이 읽기 좋게 정리해서 보고** — 출력값을 바탕으로 다음을 사용자에게 보여준다.
   - 전체 누적 호출 횟수
   - 호출 횟수 내림차순으로 스킬별 횟수와 마지막 호출 시각
   - 가장 많이 쓴 스킬 1~3개를 한 줄로 요약

3. **데이터가 없을 때** — 파일이 없거나 비어 있으면, 아직 hook가 한 번도 기록하지 않은 것이다.
   "아직 기록된 호출이 없다"고 알리고, hook가 갓 등록된 경우 `/hooks`를 한 번 열거나 재시작이 필요할 수 있음을 안내한다.

## 참고

- 통계 파일 위치: `~/.claude/skill-stats.json`
- 기록 주체: `~/.claude/hooks/skill-usage-log.sh` (PreToolUse / matcher `Skill`)
- 이 `skill-stat` 자신도 Skill 도구로 호출되므로 통계에 함께 집계된다.
- 통계를 초기화하려면 `printf '{}\n' > ~/.claude/skill-stats.json` 을 실행하면 된다.
