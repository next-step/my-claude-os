#!/usr/bin/env python3
"""
PostToolUse 훅 — Claude 작업 내역을 JSONL로 최소 토큰으로 기록.

기록 대상: Agent / Write / Edit / Bash(description 있을 때)
pid(prompt-ID)를 포함해 프롬프트↔작업 매핑을 가능하게 한다.
출력: .claude/logs/sessions/YYYY-MM-DD.jsonl
"""
import json, os, sys
from datetime import datetime

TRACKED = {"Agent", "Write", "Edit", "Bash"}

# 스크립트 위치 기준 절대경로 — CWD에 무관하게 항상 같은 위치에 로그를 쓴다
HOOKS_DIR = os.path.dirname(os.path.abspath(__file__))   # .claude/hooks/
CLAUDE_DIR = os.path.dirname(HOOKS_DIR)                  # .claude/
LOGS_DIR = os.path.join(CLAUDE_DIR, "logs")              # .claude/logs/

# 이 경로에 대한 변경은 재귀 로깅을 막기 위해 스킵
LOGS_DIR_ABS = os.path.abspath(LOGS_DIR)

def flat(v, n=120):
    """다양한 응답 형식에서 텍스트를 추출하고 n자로 자른다."""
    if isinstance(v, list):
        v = " ".join(i.get("text", "") for i in v if isinstance(i, dict))
    elif isinstance(v, dict):
        v = v.get("content", v.get("text", "")) or str(v)
        if isinstance(v, (list, dict)):
            return flat(v, n)
    s = str(v).strip()
    return s[:n] + "…" if len(s) > n else s

try:
    payload = json.load(sys.stdin)
except Exception:
    sys.exit(0)

op = payload.get("tool_name", "")
if op not in TRACKED:
    sys.exit(0)

inp = payload.get("tool_input", {})

# Bash: description 없는 탐색 명령 스킵
if op == "Bash" and not inp.get("description"):
    sys.exit(0)

# Write/Edit: 로그 파일 자신을 기록하지 않음 (재귀 방지)
if op in {"Write", "Edit"}:
    path = inp.get("file_path", "")
    if os.path.abspath(path).startswith(LOGS_DIR_ABS):
        sys.exit(0)

# 현재 prompt-ID 읽기 (없으면 빈 문자열)
pid_file = os.path.join(LOGS_DIR, ".current_pid")
try:
    pid = open(pid_file).read().strip()
except Exception:
    pid = ""

now = datetime.now()
entry = {"pid": pid, "t": now.strftime("%H:%M"), "op": op.lower()}

if op == "Agent":
    entry.update({
        "desc": inp.get("description", ""),
        "agent": inp.get("subagent_type", ""),
        "result": flat(payload.get("tool_response", ""), 200),
    })
elif op == "Write":
    entry.update({
        "path": inp.get("file_path", ""),
        "size": len(inp.get("content", "")),
    })
elif op == "Edit":
    entry.update({
        "path": inp.get("file_path", ""),
        "from": flat(inp.get("old_string", ""), 80),
        "to": flat(inp.get("new_string", ""), 80),
    })
elif op == "Bash":
    entry.update({
        "desc": inp.get("description", ""),
        "cmd": flat(inp.get("command", ""), 100),
    })

log_path = os.path.join(LOGS_DIR, "sessions", f"{now.strftime('%Y-%m-%d')}.jsonl")
os.makedirs(os.path.dirname(log_path), exist_ok=True)
with open(log_path, "a", encoding="utf-8") as f:
    f.write(json.dumps(entry, ensure_ascii=False) + "\n")
