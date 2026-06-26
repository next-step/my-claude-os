#!/bin/bash
# 스크립트 자기 위치 기준으로 프로젝트 루트(.claude/hooks → ../..)로 이동.
# 절대경로를 박지 않아 다른 환경/경로에서도 그대로 동작한다.
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR/../.." || exit 1

# claude 바이너리는 PATH에서 찾는다. cron의 제한된 PATH를 보완해
# Homebrew(Apple Silicon/Intel) 경로를 추가한 뒤 실행한다.
export PATH="/opt/homebrew/bin:/usr/local/bin:$PATH"
claude -p "/remind"
