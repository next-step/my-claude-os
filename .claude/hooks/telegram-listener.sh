#!/bin/bash
# 텔레그램 인바운드 브리지 (폴링 방식)
# ─────────────────────────────────────────────────────────────
# 폰에서 봇에게 보낸 "/capture ..." 같은 명령을 받아 로컬 claude로 실행한다.
# remind-cron.sh와 동일하게 cron이 1분마다 호출하는 것을 전제로 한다.
#
# 보안 3종 (반드시 유지):
#   1) chat_id 화이트리스트 — telegram.json의 내 chat_id가 보낸 것만 처리
#   2) 명령 화이트리스트   — ALLOWED_COMMANDS에 등록된 슬래시 명령만 실행
#   3) eval 미사용         — 사용자 텍스트를 셸로 재평가하지 않고 인자로만 전달

set -u

# 스크립트 위치 기준 프로젝트 루트(.claude/hooks → ../..)로 이동.
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR/../.." || exit 1

# cron의 제한된 PATH 보완 — claude 바이너리/jq를 찾기 위함.
export PATH="/opt/homebrew/bin:/usr/local/bin:$PATH"

DATA_DIR=".claude/data"
CRED="$DATA_DIR/telegram.json"
OFFSET_FILE="$DATA_DIR/telegram-offset.txt"
LOCK_DIR="$DATA_DIR/.telegram-listener.lock"

# ── 허용 명령 화이트리스트 ──────────────────────────────────
# 새 명령을 열려면 여기에 슬래시 명령만 추가하면 된다 (예: "/done" "/plan").
ALLOWED_COMMANDS=("/capture")

# ── 동시 실행 방지 (claude -p가 1분 넘게 걸릴 때 cron 중첩 차단) ──
# macOS엔 flock이 없으므로 mkdir의 원자성을 락으로 사용한다.
# 10분 이상 묵은 락은 죽은 프로세스 잔재로 보고 회수한다(데드락 방지).
if [ -d "$LOCK_DIR" ]; then
  AGE=$(( $(date +%s) - $(stat -f %m "$LOCK_DIR" 2>/dev/null || echo 0) ))
  if [ "$AGE" -gt 600 ]; then
    rmdir "$LOCK_DIR" 2>/dev/null
  fi
fi
if ! mkdir "$LOCK_DIR" 2>/dev/null; then
  echo "[telegram-listener] 이전 실행이 진행 중 — 건너뜀"
  exit 0
fi
trap 'rmdir "$LOCK_DIR" 2>/dev/null' EXIT

# ── 자격증명 로드 ───────────────────────────────────────────
if [ ! -s "$CRED" ]; then
  echo "[telegram-listener] $CRED 없음 — 중단"
  exit 1
fi
BOT_TOKEN=$(jq -r '.bot_token' "$CRED")
MY_CHAT_ID=$(jq -r '.chat_id' "$CRED")
API="https://api.telegram.org/bot${BOT_TOKEN}"

# ── 텔레그램 발송 헬퍼 (text는 4000자로 자름: API 한도 4096) ──
send_tg() {
  local text="$1"
  text="${text:0:4000}"
  curl -s "${API}/sendMessage" \
    --data-urlencode "chat_id=${MY_CHAT_ID}" \
    --data-urlencode "text=${text}" \
    -o /dev/null
}

# ── 명령 허용 여부 검사 ─────────────────────────────────────
is_allowed() {
  local cmd="$1"
  for a in "${ALLOWED_COMMANDS[@]}"; do
    [ "$cmd" = "$a" ] && return 0
  done
  return 1
}

# ── offset(마지막으로 확정 처리한 update_id+1) 로드 ──────────
OFFSET=0
[ -s "$OFFSET_FILE" ] && OFFSET=$(cat "$OFFSET_FILE")

# ── getUpdates 폴링 ────────────────────────────────────────
RESP=$(curl -s "${API}/getUpdates?offset=${OFFSET}&timeout=0")
if [ "$(echo "$RESP" | jq -r '.ok')" != "true" ]; then
  echo "[telegram-listener] getUpdates 실패: $(echo "$RESP" | jq -r '.description // "unknown"')"
  exit 1
fi

COUNT=$(echo "$RESP" | jq '.result | length')
if [ "$COUNT" -eq 0 ]; then
  exit 0   # 새 메시지 없음
fi

MAX_UID="$OFFSET"

# 각 업데이트를 base64로 통째 인코딩해 텍스트 내 개행/탭 안전 처리.
for row in $(echo "$RESP" | jq -r '.result[] | @base64'); do
  field() { echo "$row" | base64 --decode | jq -r "$1"; }

  uid=$(field '.update_id')
  cid=$(field '.message.chat.id // empty')
  text=$(field '.message.text // empty')

  # offset은 처리/스킵 여부와 무관하게 항상 전진시켜 재처리를 막는다.
  if [ "$uid" -ge "$MAX_UID" ]; then
    MAX_UID=$((uid + 1))
  fi

  # 보안 1: 내 chat_id가 아니면 무시 (응답도 하지 않음 — 정보 노출 방지).
  [ -n "$cid" ] && [ "$cid" = "$MY_CHAT_ID" ] || continue
  [ -n "$text" ] || continue

  # 첫 토큰을 명령으로 파싱. 그룹용 "/capture@botname" 형태의 @봇이름은 제거.
  cmd="${text%% *}"
  cmd="${cmd%%@*}"
  args="${text#"$cmd"}"
  args="${args# }"   # 앞 공백 한 칸 제거

  # 보안 2: 화이트리스트에 없는 명령은 거절 안내만.
  if ! is_allowed "$cmd"; then
    send_tg "⚠️ 허용되지 않은 명령이에요: ${cmd}
사용 가능: ${ALLOWED_COMMANDS[*]}"
    continue
  fi

  if [ -z "$args" ]; then
    send_tg "⚠️ ${cmd} 뒤에 내용을 적어주세요. 예: ${cmd} 장보기"
    continue
  fi

  # 보안 3: eval 없이 인자로만 전달. (변수 확장은 명령 치환을 재실행하지 않음)
  echo "[telegram-listener] 실행: ${cmd} ${args}"
  OUT=$(claude -p "${cmd} ${args}" 2>&1)
  RC=$?

  if [ $RC -eq 0 ]; then
    send_tg "$OUT"
  else
    send_tg "❌ 실행 실패 (code ${RC})
${OUT}"
  fi
done

# ── offset 확정 저장 ───────────────────────────────────────
echo "$MAX_UID" > "$OFFSET_FILE"
