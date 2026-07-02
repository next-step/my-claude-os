#!/usr/bin/env bash
# 스킬(Skill 툴)이 실행될 때마다 호출 기록을 남기는 PreToolUse hook 스크립트.
#
# 동작 방식:
#   - Claude Code는 hook을 실행할 때 이벤트 정보를 JSON 형태로 stdin에 넘겨준다.
#   - 그 JSON에서 .tool_input.skill (실행된 스킬 이름)을 꺼내,
#     .claude/logs/skill-usage.log 파일에 "시각  스킬이름" 한 줄을 덧붙인다.
#   - 호출 횟수는 해당 스킬 이름이 들어간 줄 수를 세면 된다.
#       예) grep -c " git-commit$" .claude/logs/skill-usage.log
#
# 종료 코드 0으로 끝내면 원래 작업(스킬 실행)은 그대로 진행된다.

set -euo pipefail

# 이 스크립트(.claude/hooks/) 기준으로 프로젝트의 .claude 디렉토리를 찾는다.
HOOK_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CLAUDE_DIR="$(dirname "$HOOK_DIR")"
LOG_DIR="$CLAUDE_DIR/logs"
LOG_FILE="$LOG_DIR/skill-usage.log"

# stdin으로 들어온 hook 이벤트 JSON을 읽는다.
INPUT="$(cat)"

# 실행된 스킬 이름을 추출한다. 없으면 조용히 종료(다른 툴 호출일 수 있음).
SKILL_NAME="$(printf '%s' "$INPUT" | jq -r '.tool_input.skill // empty')"
[ -z "$SKILL_NAME" ] && exit 0

# 로그 디렉토리를 보장하고, "시각  스킬이름" 한 줄을 덧붙인다.
mkdir -p "$LOG_DIR"
printf '%s\t%s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$SKILL_NAME" >> "$LOG_FILE"

exit 0
