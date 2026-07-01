#!/usr/bin/env bash
# 기획 결정 변경 로그용 PostToolUse hook.
# Edit/Write 도구로 OS.md(서비스 단일 진실 출처)가 수정될 때마다
# 프로젝트 루트의 DECISIONS.md 에 "언제·어떤 파일·어떤 도구로" 한 줄을 append 한다.
# 변경의 '왜'까지 자동으로 알 수는 없으므로(페이로드에 의도가 없음),
# 이 로그는 "언제 청사진이 바뀌었나"의 추적선이고, 상세 사유는 OS.md 본문·커밋 메시지가 보관한다.
# 어떤 경우에도 도구 실행 결과에 영향을 주지 않도록 항상 exit 0 한다.

# stdin 의 hook payload 한 번만 읽어두기
payload="$(cat)"

tool="$(printf '%s' "$payload" | jq -r '.tool_name // empty' 2>/dev/null)"
fpath="$(printf '%s' "$payload" | jq -r '.tool_input.file_path // empty' 2>/dev/null)"

# 파일 경로가 없으면 조용히 종료
[ -z "$fpath" ] && exit 0

# OS.md 수정일 때만 기록 (다른 파일 편집은 무시)
case "$(basename "$fpath")" in
  OS.md) ;;
  *) exit 0 ;;
esac

# 로그 위치: 프로젝트 루트. CLAUDE_PROJECT_DIR 가 없으면 OS.md 의 디렉터리로 폴백.
root="${CLAUDE_PROJECT_DIR:-$(dirname "$fpath")}"
log="${root}/DECISIONS.md"

# 헤더가 없으면 한 번 생성
if [ ! -f "$log" ]; then
  {
    printf '# 결정 변경 로그 (DECISIONS.md)\n\n'
    printf '> OS.md(청사진)가 수정될 때마다 decision-log hook 이 자동으로 한 줄씩 남긴다.\n'
    printf '> "왜" 바꿨는지는 OS.md 본문과 커밋 메시지를 참조한다.\n\n'
  } > "$log"
fi

now="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
printf -- '- %s · OS.md %s됨\n' "$now" "$tool" >> "$log"

exit 0
