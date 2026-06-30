#!/usr/bin/env python3
"""PreToolUse 훅: git commit 직전, 스테이징된 변경에 "머신 절대경로"가 있으면 커밋을 막는다.

AI가 작업하다 /Users/<나> · /home/<나> 같은 절대경로를 실수로 남기면 다른 환경에서 깨진다.
사람이 AI 코드를 다 읽지 않으므로, 커밋 시점에 스테이징된 "추가된 줄"을 검사해
발견되면 exit 2 로 커밋을 결정적으로 차단한다(없으면 항상 통과).

검사 대상은 staged diff 의 추가(+) 줄만 — 기존/문맥 줄은 건드리지 않아 오탐을 줄인다.
"""
import json
import os
import re
import subprocess
import sys

PAT = re.compile(r"/Users/[A-Za-z0-9._-]+|/home/[A-Za-z0-9._-]+")

# 이 가드 파일 자체는 패턴(/Users/...)을 정의상 포함하므로 검사에서 제외(자기 차단 방지).
SELF = ".claude/hooks/check-abs-paths.py"


def main() -> int:
    try:
        payload = json.load(sys.stdin)
    except Exception:
        return 0  # 입력 이상 시 흐름을 막지 않는다.

    if payload.get("tool_name") != "Bash":
        return 0
    cmd = (payload.get("tool_input") or {}).get("command", "")
    if "git commit" not in cmd:
        return 0  # 커밋 명령일 때만 검사

    root = os.environ.get("CLAUDE_PROJECT_DIR") or os.getcwd()
    try:
        diff = subprocess.run(
            ["git", "diff", "--cached", "--unified=0", "--no-color"],
            cwd=root, capture_output=True, text=True, check=True,
        ).stdout
    except Exception:
        return 0

    hits, cur = [], None
    for line in diff.splitlines():
        if line.startswith("+++ b/"):
            cur = line[6:]
        elif line.startswith("+") and not line.startswith("+++"):
            if cur == SELF:
                continue  # 가드 파일 자체는 건너뜀
            if PAT.search(line):
                hits.append(f"{cur}: {line[1:].strip()[:100]}")

    if hits:
        sys.stderr.write(
            "❌ 커밋 차단: 스테이징된 변경에 머신 절대경로가 있습니다 (다른 환경에서 깨짐).\n"
            "   $CLAUDE_PROJECT_DIR 등 상대/환경변수 경로로 바꾼 뒤 다시 커밋하세요:\n"
        )
        for h in hits[:20]:
            sys.stderr.write("   - " + h + "\n")
        return 2  # 도구(커밋) 차단

    return 0


if __name__ == "__main__":
    sys.exit(main())
