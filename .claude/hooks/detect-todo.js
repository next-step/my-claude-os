#!/usr/bin/env node
/**
 * UserPromptSubmit 훅 — 자연어 할일 감지기
 *
 * 사용자가 /capture 를 직접 치지 않아도, 프롬프트에 "할일 뉘앙스"가 있으면
 * Claude에게 capture 제안을 힌트로 주입한다.
 *
 * 설계 원칙
 * - 기본은 침묵: 패턴에 안 걸리면 아무것도 출력하지 않는다.
 * - 결정은 사용자: 자동 저장이 아니라 "제안만" 하도록 힌트를 준다.
 * - 오탐 방지: 명령/작업 지시/질문은 할일이 아니므로 먼저 걸러낸다.
 */
const fs = require("fs");

let input;
try {
  input = JSON.parse(fs.readFileSync(0, "utf8")); // stdin
} catch {
  process.exit(0); // 입력 파싱 실패 시 조용히 통과
}

const prompt = (input.prompt || "").trim();

// 1) 할일 뉘앙스: "혼잣말 메모" 느낌의 표현
const TODO_PATTERN = /(해야지|해야겠|해야 하|하기로|잊지\s*말|기억해야|챙겨야|사야|연락해야|예약해야|신청해야)/;

// 2) 제외: 명백한 작업 지시·질문·슬래시 커맨드는 할일이 아니다
const COMMAND_PATTERN = /(만들어|수정|고쳐|고치|분석|실행|보여|알려|설명|확인해|찾아|왜|어떻게|뭐|어때|줘\s*$|\?$)/;
const isCommand = prompt.startsWith("/") || COMMAND_PATTERN.test(prompt);

if (TODO_PATTERN.test(prompt) && !isCommand) {
  console.log(
    JSON.stringify({
      hookSpecificOutput: {
        hookEventName: "UserPromptSubmit",
        additionalContext:
          "[capture 힌트] 방금 입력에 할일 뉘앙스가 감지되었습니다. " +
          "사용자의 실제 요청을 처리하는 것이 우선이지만, 별다른 요청이 없다면 " +
          "핵심 키워드를 뽑아 `/capture {키워드}` 실행을 한 줄로 제안하세요. " +
          "자동 저장하지 말고 제안만 하세요 (결정은 사용자 몫).",
      },
    })
  );
}
