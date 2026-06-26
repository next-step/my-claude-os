#!/usr/bin/env node
/**
 * tests/unit.test.js — L2: 훅 단위 테스트 (결정적)
 *
 * detect-todo.js 는 stdin(JSON) → stdout(JSON 또는 빈 출력)인 순수 함수에 가깝다.
 * 그래서 npm 의존성 없이 child_process 로 실제 훅을 실행하고 출력만 검증한다.
 *
 * 핵심 계약 두 가지:
 *   - 할일 뉘앙스가 있고 명령/질문/슬래시가 아니면 → capture 힌트를 출력한다.
 *   - 그 외(명령·질문·슬래시·무관한 말·빈 입력)는 → 아무것도 출력하지 않는다(침묵).
 */
const { execFileSync } = require("node:child_process");
const path = require("node:path");

const HOOK = path.join(__dirname, "..", ".claude", "hooks", "detect-todo.js");

/** 훅을 prompt 하나로 실행하고 stdout(trim) 을 돌려준다. */
function runHook(prompt) {
  const input = JSON.stringify({ prompt });
  return execFileSync("node", [HOOK], { input, encoding: "utf8" }).trim();
}

/** 출력이 capture 힌트인가 (유효 JSON + 올바른 hook 형식 + 힌트 문구 포함) */
function isCaptureHint(out) {
  if (!out) return false;
  let parsed;
  try {
    parsed = JSON.parse(out);
  } catch {
    return false;
  }
  const ctx = parsed?.hookSpecificOutput?.additionalContext ?? "";
  return (
    parsed?.hookSpecificOutput?.hookEventName === "UserPromptSubmit" &&
    ctx.includes("capture 힌트")
  );
}

// [입력, 기대] — 기대: "hint" = 힌트 출력, "silent" = 빈 출력
const CASES = [
  // 할일 뉘앙스 → 힌트
  ["우유 사야지", "hint"],
  ["엄마한테 연락해야지", "hint"],
  ["병원 예약해야겠다", "hint"],
  ["이거 잊지 말자", "hint"],
  // 명령/작업 지시 → 침묵 (오탐 방지)
  ["이 버그 좀 고쳐줘", "silent"],
  ["README 만들어줘", "silent"],
  ["로그인 코드 분석해줘", "silent"],
  // 질문 → 침묵
  ["이거 왜 안돼?", "silent"],
  ["어떻게 배포해?", "silent"],
  // 슬래시 커맨드 → 침묵
  ["/capture 장보기", "silent"],
  // 명령 패턴이 할일 패턴을 이긴다 (precedence 문서화)
  ["회의 자료 만들어야지", "silent"],
  // 무관한 평범한 말 / 빈 입력 → 침묵
  ["오늘 날씨 좋네", "silent"],
  ["", "silent"],
];

let pass = 0;
let fail = 0;
console.log("── L2: 훅 단위 테스트 (detect-todo.js) ──────────");

for (const [prompt, expected] of CASES) {
  const out = runHook(prompt);
  const actual = isCaptureHint(out) ? "hint" : out === "" ? "silent" : "other";
  const label = JSON.stringify(prompt).padEnd(28);
  if (actual === expected) {
    pass++;
    console.log(`  \x1b[32m✓\x1b[0m ${label} → ${actual}`);
  } else {
    fail++;
    console.log(
      `  \x1b[31m✗\x1b[0m ${label} → 기대:${expected} 실제:${actual}`
    );
    if (actual === "other") console.log(`      예상밖 출력: ${out}`);
  }
}

console.log("────────────────────────────────────────────────");
console.log(`L2 결과: ${pass} pass / ${fail} fail`);
process.exit(fail === 0 ? 0 : 1);
