#!/usr/bin/env bash
#
# notify-telegram.sh — 주식 OS 모바일 푸시 전송 헬퍼 (Telegram)
#
# 역할: 메시지 한 덩어리를 받아 내 Telegram 봇으로 푸시한다.
#       기본은 HTML 서식(<b>·<i>)으로 보내 가독성을 살리고,
#       혹시 HTML 파싱이 실패하면 태그를 벗겨 평문으로 자동 재전송한다(전달 보장).
#       토큰/대상은 프로젝트 루트 .env(TELEGRAM_BOT_TOKEN, TELEGRAM_CHAT_ID)에서 읽는다.
#       이 스크립트는 "전달 채널"만 담당하고, 내용 생성은 daily-brief 등 상위가 맡는다.
#       (마크다운→HTML 변환은 scripts/md-to-telegram.sh 가 담당.)
#
# 사용법:
#   ./scripts/notify-telegram.sh "보낼 메시지"
#   echo "보낼 메시지" | ./scripts/notify-telegram.sh
#   cat stock/briefings/2026-06-29.md | ./scripts/md-to-telegram.sh | ./scripts/notify-telegram.sh
#
set -euo pipefail

# --- 프로젝트 루트의 .env 로드 ---
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [ -f "$ROOT/.env" ]; then
  set -a            # 이후 변수들을 자동 export
  # shellcheck disable=SC1091
  . "$ROOT/.env"
  set +a
fi

# --- 필수 시크릿 확인 (없으면 친절히 안내하고 종료) ---
: "${TELEGRAM_BOT_TOKEN:?[notify] .env에 TELEGRAM_BOT_TOKEN 이 없습니다. .env.example 참고}"
: "${TELEGRAM_CHAT_ID:?[notify] .env에 TELEGRAM_CHAT_ID 이 없습니다. .env.example 참고}"

# --- 메시지: 첫 인자 우선, 없으면 표준입력(stdin) ---
if [ "$#" -ge 1 ]; then
  MESSAGE="$1"
else
  MESSAGE="$(cat)"
fi

if [ -z "${MESSAGE// /}" ]; then
  echo "[notify] 보낼 메시지가 비어 있습니다." >&2
  exit 1
fi

# Telegram 메시지 길이 제한(4096자) — 넘으면 잘라서 보냄
# (태그가 잘려 HTML이 깨져도 아래 평문 폴백이 처리한다.)
if [ "${#MESSAGE}" -gt 4000 ]; then
  MESSAGE="${MESSAGE:0:3990}…(생략)"
fi

API="https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/sendMessage"

# send <text> [parse_mode]  — parse_mode 를 비우면 평문 전송
send() {
  local text="$1" mode="${2:-}"
  local args=(--data-urlencode "chat_id=${TELEGRAM_CHAT_ID}"
              --data-urlencode "text=${text}"
              --data "disable_web_page_preview=true")
  [ -n "$mode" ] && args+=(--data "parse_mode=${mode}")
  curl -sS -X POST "$API" "${args[@]}"
}

# --- 1차: HTML 서식으로 전송 ---
RESP="$(send "$MESSAGE" HTML)"

# --- HTML 파싱 실패 등 → 태그 벗겨 평문으로 재전송 ---
if ! echo "$RESP" | grep -q '"ok":true'; then
  PLAIN="$(printf '%s' "$MESSAGE" \
    | sed -E -e 's/<[^>]+>//g' -e 's/&lt;/</g' -e 's/&gt;/>/g' -e 's/&amp;/\&/g')"
  RESP="$(send "$PLAIN")"
fi

if echo "$RESP" | grep -q '"ok":true'; then
  echo "✅ Telegram 전송 완료"
else
  echo "❌ Telegram 전송 실패: $RESP" >&2
  exit 1
fi
