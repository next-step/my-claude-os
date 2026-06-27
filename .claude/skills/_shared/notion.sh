#!/usr/bin/env bash
#
# notion.sh — 할일 (Claude OS) Notion DB 직접 호출 헬퍼
#
# 왜 이 스크립트가 있나:
#   read/write/update는 입력이 정해지면 출력도 정해지는 "결정론적" API 호출이다.
#   여기에 LLM 서브 에이전트(Agent())를 띄우면 콜드 스타트·프롬프트 재독해·curl 조립
#   추론까지 LLM이 하느라 느리다. 스킬 오케스트레이터가 이 스크립트를 Bash로 직접
#   호출하면 그 오버헤드가 통째로 사라진다. (추론이 필요한 분류/인터뷰/알럿만 Agent로 남긴다.)
#
# 사용법:
#   notion.sh read [status]              # status 생략 = 전체 조회. 예: notion.sh read draft
#   echo '<flat-json>' | notion.sh write     # 새 항목 생성. 생성된 항목(flat json) 반환
#   echo '<flat-json>' | notion.sh update <page_id>   # 일부 필드 수정. 수정된 항목 반환
#
# flat-json 필드 (있는 것만 넣으면 됨):
#   { "title", "category", "status", "captured_at",
#     "recurrence" (once|daily|null), "due_date" (YYYY-MM-DD|null),
#     "time" (라벨|null), "detail" (내용|null) }
#
# 출력: read 는 항목 배열, write/update 는 단일 항목 (모두 아래 flat 형식)
#   { id, title, category, status, captured_at, recurrence, due_date, time, detail }
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
NOTION_JSON="$SCRIPT_DIR/../../data/notion.json"
NOTION_VERSION="2022-06-28"

if [[ ! -f "$NOTION_JSON" ]]; then
  echo '{"ok":false,"error":"notion.json not found"}' >&2
  exit 1
fi

TOKEN="$(jq -r '.token' "$NOTION_JSON")"
DB_ID="$(jq -r '.database_id' "$NOTION_JSON")"

# Notion API 응답(results 배열) → flat json 배열로 변환하는 공통 jq 필터
READ_FILTER='[.results[] | {
  id: .id,
  title:       (.properties.Title.title[0].plain_text // null),
  category:    (.properties.Category.select.name // null),
  status:      (.properties.Status.select.name // null),
  captured_at: (.properties.CapturedAt.date.start // null),
  recurrence:  (.properties.Recurrence.select.name // null),
  due_date:    (.properties.DueDate.date.start // null),
  time:        (.properties.Time.rich_text[0].plain_text // null),
  detail:      (.properties.Detail.rich_text[0].plain_text // null)
}]'

# 단일 페이지 응답 → flat json 객체
ONE_FILTER='{results:[.]} | '"$READ_FILTER"' | .[0]'

# flat json → Notion properties 페이로드. 입력에 존재하는 키만 변환한다.
build_properties() {
  jq -n --argjson d "$1" '
    def prop($k; $v):
      if   $k=="title"       then {Title:{title:[{text:{content:$v}}]}}
      elif $k=="category"    then {Category:{select:{name:$v}}}
      elif $k=="status"      then {Status:{select:{name:$v}}}
      elif $k=="captured_at" then {CapturedAt:{date:{start:$v}}}
      elif $k=="recurrence"  then {Recurrence: (if $v==null then {select:null} else {select:{name:$v}} end)}
      elif $k=="due_date"    then {DueDate: (if $v==null then {date:null} else {date:{start:$v}} end)}
      elif $k=="time"        then {Time: (if $v==null then {rich_text:[]} else {rich_text:[{text:{content:$v}}]} end)}
      elif $k=="detail"      then {Detail: (if $v==null then {rich_text:[]} else {rich_text:[{text:{content:$v}}]} end)}
      else {} end;
    [$d | to_entries[] | prop(.key; .value)] | add // {}
  '
}

cmd="${1:-}"

case "$cmd" in
  read)
    status="${2:-}"
    if [[ -n "$status" ]]; then
      body="$(jq -n --arg s "$status" '{
        filter: {property:"Status", select:{equals:$s}},
        sorts: [{property:"CapturedAt", direction:"ascending"}]
      }')"
    else
      body='{"sorts":[{"property":"CapturedAt","direction":"ascending"}]}'
    fi
    curl -s -X POST "https://api.notion.com/v1/databases/$DB_ID/query" \
      -H "Authorization: Bearer $TOKEN" \
      -H "Notion-Version: $NOTION_VERSION" \
      -H "Content-Type: application/json" \
      -d "$body" | jq "$READ_FILTER"
    ;;

  write)
    input="$(cat)"
    props="$(build_properties "$input")"
    payload="$(jq -n --arg db "$DB_ID" --argjson props "$props" \
      '{parent:{database_id:$db}, properties:$props}')"
    curl -s -X POST "https://api.notion.com/v1/pages" \
      -H "Authorization: Bearer $TOKEN" \
      -H "Notion-Version: $NOTION_VERSION" \
      -H "Content-Type: application/json" \
      -d "$payload" | jq "$ONE_FILTER"
    ;;

  update)
    page_id="${2:-}"
    if [[ -z "$page_id" ]]; then
      echo '{"ok":false,"error":"update requires <page_id>"}' >&2
      exit 1
    fi
    input="$(cat)"
    props="$(build_properties "$input")"
    payload="$(jq -n --argjson props "$props" '{properties:$props}')"
    curl -s -X PATCH "https://api.notion.com/v1/pages/$page_id" \
      -H "Authorization: Bearer $TOKEN" \
      -H "Notion-Version: $NOTION_VERSION" \
      -H "Content-Type: application/json" \
      -d "$payload" | jq "$ONE_FILTER"
    ;;

  *)
    echo "usage: notion.sh {read [status] | write | update <page_id>}" >&2
    exit 1
    ;;
esac
