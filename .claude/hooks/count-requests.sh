#!/usr/bin/env bash
# UserPromptSubmit 훅: 사용자가 요청(프롬프트 제출)할 때마다 호출 횟수를 파일에 기록한다.
#
# 동작 방식:
#   - Claude Code가 이 스크립트를 실행하며, stdin으로 이벤트 정보(JSON)를 넘긴다.
#   - 우리는 카운트 파일의 숫자를 1 올리고, 로그 파일에 시각과 함께 한 줄을 남긴다.
#   - 표준출력에 아무것도 내보내지 않으므로 대화에는 영향이 없다(조용히 기록만).

set -euo pipefail

# 기록 위치: 프로젝트의 .claude 폴더 기준
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COUNT_FILE="$DIR/request-count.txt"
LOG_FILE="$DIR/request-log.txt"

# 현재 카운트 읽기(없으면 0), 1 증가
count=0
if [[ -f "$COUNT_FILE" ]]; then
  count=$(cat "$COUNT_FILE" 2>/dev/null || echo 0)
fi
count=$((count + 1))

# 카운트 파일 갱신
echo "$count" > "$COUNT_FILE"

# 로그 파일에 "횟수  시각" 한 줄 추가
printf '%s\t%s\n' "$count" "$(date '+%Y-%m-%d %H:%M:%S')" >> "$LOG_FILE"

exit 0
