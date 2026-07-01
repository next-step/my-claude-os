#!/usr/bin/env python3
"""PreToolUse 훅: Skill 도구가 호출될 때마다 스킬별 호출 횟수를 기록한다.

stdin 으로 들어오는 훅 페이로드(JSON)에서 호출된 스킬 이름을 읽어
.claude/skill-stats.json 에 누적 기록한다. 도구 실행은 막지 않는다(항상 exit 0).
"""
import json
import os
import sys
from datetime import datetime


def project_dir() -> str:
    # Claude Code 가 제공하는 프로젝트 루트 경로. 없으면 cwd 로 폴백.
    return os.environ.get("CLAUDE_PROJECT_DIR") or os.getcwd()


def main() -> int:
    try:
        payload = json.load(sys.stdin)
    except Exception:
        return 0  # 입력이 비정상이어도 도구 흐름은 막지 않는다.

    if payload.get("tool_name") != "Skill":
        return 0

    skill = (payload.get("tool_input") or {}).get("skill")
    if not skill:
        return 0

    stats_path = os.path.join(project_dir(), ".claude", "skill-stats.json")

    try:
        with open(stats_path, "r", encoding="utf-8") as f:
            stats = json.load(f)
    except (FileNotFoundError, json.JSONDecodeError):
        stats = {"skills": {}, "total": 0}

    skills = stats.setdefault("skills", {})
    entry = skills.setdefault(skill, {"count": 0, "first_used": None, "last_used": None})

    now = datetime.now().isoformat(timespec="seconds")
    entry["count"] += 1
    entry["last_used"] = now
    if not entry.get("first_used"):
        entry["first_used"] = now
    stats["total"] = stats.get("total", 0) + 1

    os.makedirs(os.path.dirname(stats_path), exist_ok=True)
    with open(stats_path, "w", encoding="utf-8") as f:
        json.dump(stats, f, ensure_ascii=False, indent=2)

    return 0


if __name__ == "__main__":
    sys.exit(main())
