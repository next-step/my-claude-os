#!/usr/bin/env bash
# mermaid(.mmd) → PNG 렌더. 시스템 Chrome(headless) + --no-sandbox 사용.
# 사용법: render-mermaid.sh <input.mmd> <output.png>
# /diagram 스킬이 이 스크립트를 호출하고, 결과 PNG를 Read로 직접 봐서 검증한다.
set -euo pipefail

in="${1:?입력 .mmd 경로 필요}"
out="${2:?출력 .png 경로 필요}"

# 시스템 Chrome 경로 (없으면 환경변수 CHROME_PATH 로 덮어쓰기)
chrome="${CHROME_PATH:-/Applications/Google Chrome.app/Contents/MacOS/Google Chrome}"

cfg="$(mktemp)"
trap 'rm -f "$cfg"' EXIT
printf '{"executablePath":"%s","args":["--no-sandbox"]}\n' "$chrome" > "$cfg"

mkdir -p "$(dirname "$out")"

# puppeteer가 자체 chromium을 받지 않게 하고, 시스템 Chrome으로 렌더
PUPPETEER_SKIP_DOWNLOAD=true \
  npx -y @mermaid-js/mermaid-cli@latest -i "$in" -o "$out" -p "$cfg" -b transparent -s 2

echo "rendered: $out"
