#!/usr/bin/env python3
"""
PreToolUse 훅: 자동매매(실주문)를 하네스 차원에서 차단한다.

왜 있나 (OS.md 원칙 1 — Human-in-the-loop):
- 이 OS에서 Claude는 매매 *추천·근거*까지만 하고, 실제 주문은 항상 사람이 승인/실행한다.
- "모델이 알아서 안 하겠지"에 기대지 않고, tossctl 실주문 명령을 **훅이 물리적으로 막는다.**

동작:
- Bash 도구 호출의 command 를 검사한다.
- 아래에 해당하면 **deny**(실행 차단):
    · tossctl order place / cancel / amend   (미체결·체결 상태를 바꾸는 주문)
    · tossctl ... --execute                  (2단계 게이트의 실제 주문 실행 플래그)
- 아래는 **허용**(조회·모의):
    · tossctl quote/market/... (조회)
    · tossctl order preview    (dry-run 미리보기 — 주문 안 나감)
    · tossctl orders list/completed, order show (조회)
- Bash 이외 도구, tossctl 이 아닌 명령은 관여하지 않는다(정상 진행).

차단은 조용히 실패하지 않는다: deny 사유를 사용자/Claude에게 돌려준다.
파싱 실패 등 예기치 못한 경우엔 막지 않는다(오탐으로 정상 작업을 방해하지 않기 위해).
"""
import sys
import json
import re


def main():
    # 1) 훅 페이로드 파싱 (실패하면 관여하지 않음 = 정상 진행)
    try:
        payload = json.load(sys.stdin)
    except Exception:
        return

    if payload.get("tool_name") != "Bash":
        return

    command = (payload.get("tool_input") or {}).get("command") or ""
    if "tossctl" not in command:
        return

    # 2) 실주문 계열 패턴
    #    - order place|cancel|amend : "orders"(복수, 조회)·"order preview"·"order show"는 제외
    mutating_order = re.search(r"\border\s+(place|cancel|amend)\b", command)
    #    - 2단계 게이트 실행 플래그
    execute_flag = "--execute" in command

    if mutating_order or execute_flag:
        reason = (
            "🚫 자동매매 차단(OS.md 원칙 1: Human-in-the-loop). "
            "Claude는 실제 주문을 내지 않는다 — 매매는 추천·근거까지만 하고, "
            "실주문은 사용자가 직접 승인/실행한다. "
            "조회(tossctl quote/market)와 dry-run(tossctl order preview)은 허용된다. "
            "정말 주문이 필요하면 사용자가 터미널에서 직접 실행할 것."
        )
        out = {
            "hookSpecificOutput": {
                "hookEventName": "PreToolUse",
                "permissionDecision": "deny",
                "permissionDecisionReason": reason,
            }
        }
        print(json.dumps(out, ensure_ascii=False))
        return

    # 3) 그 외 tossctl 명령은 관여하지 않음(정상 권한 흐름으로)


if __name__ == "__main__":
    main()
