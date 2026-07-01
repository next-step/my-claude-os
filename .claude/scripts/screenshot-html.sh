#!/usr/bin/env bash
# 디자인된 HTML 도식을 PNG 이미지로 뽑는다 (헤드리스 Chrome 스크린샷).
# 사용법: screenshot-html.sh <input.html> <output.png> [width] [height]
#   - 스크린샷은 window-size(viewport) 만큼 캡처한다. 내용이 더 길면 아래가 잘리니,
#     /diagram 검증 단계에서 Read로 보고 잘렸으면 height를 키워 다시 부른다.
set -euo pipefail

in="${1:?입력 .html 경로 필요}"
out="${2:?출력 .png 경로 필요}"
w="${3:-1040}"
h="${4:-900}"

chrome="${CHROME_PATH:-/Applications/Google Chrome.app/Contents/MacOS/Google Chrome}"

mkdir -p "$(dirname "$out")"
abs="$(cd "$(dirname "$in")" && pwd)/$(basename "$in")"

"$chrome" --headless --disable-gpu --no-sandbox --hide-scrollbars \
  --force-device-scale-factor=2 --window-size="${w},${h}" \
  --screenshot="$out" "file://${abs}" >/dev/null 2>&1

echo "screenshot: $out (${w}x${h})"
