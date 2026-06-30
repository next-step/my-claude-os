#!/usr/bin/env bash
# SessionEnd 훅 — 이 OS(시각 검증 스킬)가 "자동으로" 띄운 vite dev 서버만 정리한다.
#
# 소유권(ownership) 추적이 핵심이다. 시각 검증 스킬(visual-check / visual-regress)이
# 서버가 DOWN 이라 직접 기동할 때만 PID 마커(.claude/.visual-dev-server.pid)를 남긴다.
#   - 마커 있음 → 그 PID(와 자식)만 종료하고 마커 삭제.  ← 우리가 켠 orphan 청소
#   - 마커 없음 → 사용자가 직접 띄운 서버다 → 아무것도 안 한다.  ← 남의 서버 안 건드림
# 포트(5173)로 무차별 kill 하지 않는다 — 사용자가 켜고 쓰든 끄고 쓰든 안전하게.
set -uo pipefail

MARKER="${CLAUDE_PROJECT_DIR:-.}/.claude/.visual-dev-server.pid"
[ -f "$MARKER" ] || exit 0

PID="$(cat "$MARKER" 2>/dev/null || true)"
rm -f "$MARKER"

# PID 형식 검증(숫자만). 비정상이면 마커만 지우고 끝.
case "$PID" in
  '' | *[!0-9]*) exit 0 ;;
esac

# 살아있고, 명령줄이 우리가 띄운 dev 서버(vite/npm/node)로 보일 때만 종료한다.
# (마커가 남은 채 PID 가 재사용됐을 때 무관한 프로세스를 죽이지 않기 위한 안전 가드)
if kill -0 "$PID" 2>/dev/null; then
  CMD="$(ps -p "$PID" -o command= 2>/dev/null || true)"
  case "$CMD" in
    *vite* | *npm* | *node*)
      pkill -P "$PID" 2>/dev/null || true  # 자식(vite/esbuild) 먼저
      kill "$PID" 2>/dev/null || true       # 그다음 부모(npm)
      echo "시각 OS: 자동 기동했던 dev 서버(pid $PID) 정리함." >&2
      ;;
  esac
fi
exit 0
