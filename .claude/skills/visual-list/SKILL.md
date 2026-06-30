---
name: visual-list
description: 시각 검증 시스템에 등록된 컴포넌트(검증 대상) 목록을 보여주고, 거기서 하나를 골라 검증(visual-check)을 바로 시작할 수 있는 진입점. 각 대상의 변형 개수와 검증 상태(촬영·AI판정·갤러리)를 표시한다. 사용자가 "등록된 거 목록", "시각 검증 대상 보여줘", "뭐뭐 등록돼 있어?", "시각 검증 시작", "visual-list" 등을 말할 때 사용한다. demo-app 에서 동작한다.
---

# visual-list — 검증 대상 목록 + 선택 진입점

등록된 컴포넌트들을 보여주고, **그 자리에서 하나를 골라 검증(`visual-check`)을 시작**할 수 있게 한다.
목록만 보고 끝낼 수도 있다.

역할 경계:
- 이 스킬은 **목록 조회(읽기 전용) + 선택 받아 검증 띄우기**까지만 한다.
- 실제 검증(촬영·코드 측정·AI 판정·갤러리·점수)은 **`visual-check` 스킬**이 한다.
- 새 컴포넌트 등록은 **`visual-add`** 가 한다.

## 절차

### 1. 등록된 대상 읽기
```bash
cd demo-app && grep -oE "^  [a-z][a-zA-Z0-9-]*: \{ label: '[^']*'" src/gallery/registry.ts
```
- `src/gallery/registry.ts` 의 `REGISTRY` 에서 키·label 을 뽑는다.

### 2. 변형 개수 + 검증 상태
```bash
cd demo-app && for f in src/gallery/variants/*.ts; do echo "$(basename "$f" .ts): $(grep -c 'expected:' "$f")개"; done
cd demo-app && for d in screenshots/*/; do
  k=$(basename "$d")
  m=$([ -f "$d/measurements.json" ] && echo 촬영O || echo 촬영X)
  a=$([ -f "$d/ai-notes.json" ] && echo AI판정O || echo AI판정X)
  g=$([ -f "$d/index.html" ] && echo 갤러리O || echo 갤러리X)
  echo "$k: $m $a $g"
done 2>/dev/null
```
- `screenshots/<키>/` 가 없으면 **"미검증(등록만 됨)"** 이다.

### 3. 목록 표로 보고
```
📋 시각 검증 등록 대상 (총 N개)

| 키 | 컴포넌트 | 변형 | 검증 상태 |
|----|----------|------|-----------|
| card   | Card   | 8개 | ✅ 검증됨 |
| button | Button | 4개 | ✅ 검증됨 |
| alert  | Alert  | 5개 | ⚠️ 미검증 |
```

### 4. 선택 받기 (AskUserQuestion)
등록된 각 대상을 선택지로 제시하고, 끝에 **"목록만 보기(검증 안 함)"** 옵션도 넣는다.
- 옵션 라벨 예: `Card (8변형)`, `Button (4변형)`, `Alert (5변형)`, `목록만 보기`

### 5. 선택에 따라 분기
- **"목록만 보기"를 고르면** → 여기서 끝낸다.
- **컴포넌트를 고르면** → 그 키로 **`visual-check` 스킬을 실행**한다(Skill 도구).
  - 대상 키는 이미 정해졌으니, visual-check 의 0단계(대상 선택)는 건너뛰고 촬영→AI 판정→갤러리→점수 보고까지 진행되게 한다.
  - visual-check 가 각 변형 판정에 쓰는 단일 판정 일꾼(`visual-judge`)은 `visual-confidence` 도 공유한다.
  - 등록이 비어 있거나 미등록 키면 `visual-add` 로 먼저 등록하도록 안내한다.

## 규칙

- 목록 조회 자체는 **읽기 전용**(파일을 만들거나 바꾸지 않는다). 실제 검증은 `visual-check` 스킬에 위임한다.
- registry 에는 있는데 변형 파일이 없는 등 불일치가 보이면 그대로 알려준다.
