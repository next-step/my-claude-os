# automation — 데일리 리포트 자동화 (로컬 launchd)

매일 아침 **07:40 KST(평일)**에 `daily-report` 스킬을 헤드리스로 실행해
`04_daily/YYYY-MM-DD.md` 리포트를 자동 생성한다.

> **왜 로컬인가:** `02_portfolio/holdings.md`는 로컬 전용(gitignore)이라
> 클라우드 에이전트가 접근할 수 없다. 개인화 조언(os.md §7 불변조건)을 지키려면
> 로컬에서 실제 보유 파일을 읽어야 하므로 macOS `launchd`로 스케줄링한다.

## 구성 파일

| 파일 | 역할 |
|------|------|
| `run-daily-report.sh` | launchd가 호출하는 러너. `claude -p "데일리 리포트 만들어줘" --allowedTools "..."` 실행(최소권한) |
| `com.stock-os.daily-report.plist` | launchd 스케줄 정의(정본). 설치 시 `~/Library/LaunchAgents/`로 복사 |
| `logs/` | 실행 로그(gitignore). 30일 초과분은 러너가 자동 삭제 |

## 설치

```sh
# 1) 러너 실행권한 부여
chmod +x automation/run-daily-report.sh

# 2) plist를 LaunchAgents로 복사(정본은 프로젝트에 유지)
cp automation/com.stock-os.daily-report.plist ~/Library/LaunchAgents/

# 3) 로드(등록)
launchctl load ~/Library/LaunchAgents/com.stock-os.daily-report.plist

# 4) 등록 확인
launchctl list | grep com.stock-os.daily-report
```

## 즉시 1회 테스트

```sh
# 스케줄과 무관하게 지금 바로 한 번 실행
launchctl start com.stock-os.daily-report

# 로그 확인
tail -f automation/logs/daily-report-$(date +%Y-%m-%d).log
```

또는 러너를 직접 실행: `zsh automation/run-daily-report.sh`

## 제거 / 갱신

```sh
# 언로드(중지)
launchctl unload ~/Library/LaunchAgents/com.stock-os.daily-report.plist

# plist를 수정했다면: 정본 수정 → 복사 → 언로드 후 다시 로드
cp automation/com.stock-os.daily-report.plist ~/Library/LaunchAgents/
launchctl unload ~/Library/LaunchAgents/com.stock-os.daily-report.plist
launchctl load  ~/Library/LaunchAgents/com.stock-os.daily-report.plist
```

## 주의

- **권한(최소권한 원칙)**: 무인 실행이지만 '모든 권한 우회'는 쓰지 않는다.
  대신 `--allowedTools`로 데일리 파이프라인이 실제 쓰는 도구만 허용한다
  (`Task Read Write Edit Glob Grep WebSearch WebFetch`). **`Bash`는 불허** →
  임의 셸 실행 차단. 목록 밖 도구는 프롬프트 없이 거부된다. 결국 로컬 파일
  읽기·웹조회·`04_daily` 저장에만 쓰인다(os.md §6 가드레일과 정합).
- **Mac이 꺼져 있으면** 그 시각엔 실행되지 않는다. 잠자기(sleep) 상태였다면
  깨어날 때 놓친 작업이 실행된다.
- 한국·미국을 시간대별로 나눠 보고 싶으면 plist의 `StartCalendarInterval`에
  시각을 추가하거나 plist를 2개로 분리한다(os.md §9).
