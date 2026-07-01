#!/bin/zsh
# stock-os 데일리 리포트 자동 실행 러너 (launchd가 매일 아침 호출).
# launchd는 최소 환경에서 실행되므로 PATH·경로를 명시적으로 잡아준다.

PROJECT_DIR="/Users/yeojin/dev/my-claude-code-os"
CLAUDE_BIN="/Users/yeojin/.local/bin/claude"
NODE_BIN_DIR="/Users/yeojin/.nvm/versions/node/v20.11.0/bin"
LOG_DIR="$PROJECT_DIR/automation/logs"

# claude 런처가 node를 필요로 하므로 nvm의 node 경로를 PATH 앞에 추가.
export PATH="$NODE_BIN_DIR:/usr/local/bin:/usr/bin:/bin:$PATH"

mkdir -p "$LOG_DIR"
cd "$PROJECT_DIR" || exit 1

STAMP="$(date +%Y-%m-%d)"
LOG="$LOG_DIR/daily-report-$STAMP.log"

{
  echo "===== $(date '+%Y-%m-%d %H:%M:%S %Z') · daily-report 시작 ====="

  # 무인 실행: '모든 권한 우회'(--dangerously-skip-permissions)가 아니라
  # 데일리 파이프라인이 실제로 쓰는 도구만 최소권한으로 허용한다.
  #   - 메인 오케스트레이터: Task (subagent 호출)
  #   - market-scanner:      Read, WebSearch, WebFetch
  #   - portfolio-analyst:   Read, Write, Edit
  # 특히 Bash는 허용하지 않는다(임의 셸 실행 차단). 목록 밖 도구는 프롬프트 없이 거부됨.
  # 스킬 트리거 문구를 그대로 프롬프트로 전달 → daily-report 오케스트레이션 실행.
  "$CLAUDE_BIN" -p "데일리 리포트 만들어줘" \
    --allowedTools "Task Read Write Edit Glob Grep WebSearch WebFetch"
  RC=$?

  echo "===== $(date '+%Y-%m-%d %H:%M:%S %Z') · daily-report 종료 (exit=$RC) ====="
} >> "$LOG" 2>&1

# 오래된 로그 정리(30일 초과분 삭제).
find "$LOG_DIR" -name 'daily-report-*.log' -mtime +30 -delete 2>/dev/null

exit 0
