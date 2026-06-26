---
name: remind-when
description: remind 자동 알럿이 매일 몇 시에 실행되도록 등록돼 있는지 조회한다.
user-invocable: true
allowed-tools: Bash
---

# /remind-when 스킬 — 리마인더 시간 조회

`/remind`가 매일 저녁 자동 실행되도록 등록된 **스케줄 시각**을 보여준다.

## 사용법

```
/remind-when
```

---

## 실행 절차

> **설계 포인트 — 정본(source of truth)을 읽는다**
> remind는 시스템 crontab이 `.claude/hooks/remind-cron.sh`를 호출하는 구조다.
> 문서(SKILL.md)에 적힌 예시 시간은 낡을 수 있으므로, 이 스킬은 **실제 등록된
> crontab을 직접 읽어** 항상 현재 값을 보여준다.

### Step 1: crontab에서 remind 항목 조회 + 사람이 읽는 형식으로 변환

아래 Bash 한 줄을 실행하고, 출력된 메시지를 사용자에게 그대로 보여준다.

```bash
crontab -l 2>/dev/null | awk '
  /remind-cron\.sh/ {
    min=$1; hr=$2; dom=$3; mon=$4; dow=$5
    # 매일(* * *)이 아닌 경우 raw 표기도 함께 안내
    daily = (dom=="*" && mon=="*" && dow=="*")
    ampm = (hr+0 < 12) ? "오전" : "오후"
    h12  = (hr+0 % 12 == 0) ? 12 : (hr+0 > 12 ? hr-12 : hr)
    printf "⏰ remind 자동 알럿 시각\n\n"
    if (daily)
      printf "  매일 %s %d시 %02d분 (%02d:%02d)\n", ampm, h12, min, hr, min
    else
      printf "  cron 표현식: %s %s %s %s %s\n", min, hr, dom, mon, dow
    printf "\n  실행: /remind → draft 항목이 있으면 텔레그램으로 알림\n"
    found=1
  }
  END {
    if (!found) {
      print "⚠️ remind 자동 실행이 등록되어 있지 않아요."
      print ""
      print "  /schedule \"/remind\" 매일 17:00  처럼 등록하거나,"
      print "  crontab -e 로 .claude/hooks/remind-cron.sh 실행을 추가하세요."
    }
  }
'
```

### Step 2: (선택) 다음 실행까지 남은 시간 안내

등록돼 있으면, 현재 시각과 비교해 "오늘 N시간 뒤" 또는 "내일 실행"을 한 줄로 덧붙여 안내한다.
(crontab 값이 없으면 이 단계는 건너뛴다.)
