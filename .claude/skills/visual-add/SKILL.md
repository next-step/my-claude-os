---
name: visual-add
description: 아직 시각 검증에 등록되지 않은 컴포넌트를 시스템에 등록(셋업)한다 — props 를 분석해 변형 목록을 만들고 레지스트리에 등록하는 것까지만 한다. 촬영·AI 판정·갤러리(검증)는 하지 않는다(그건 visual-check 의 몫). 사용자가 "이 컴포넌트 시각 검증 등록해줘", "<X> 시각 셋업해줘", "시각 검증에 추가해줘" 등을 요청할 때 사용한다. demo-app 에서 동작한다.
---

# visual-add — 컴포넌트 시각 검증 등록(셋업) 전용

지정한 공통 컴포넌트를 시각 검증 시스템(`demo-app`)에 **등록**한다.
변형 목록을 만들고 레지스트리에 올려, **검증 가능한 상태로 만드는 것까지만** 한다.

**범위(중요):** 이 스킬은 *만들기(셋업)* 만 한다. **촬영·코드 측정·AI 판정·갤러리·점수는
하지 않는다.** 그건 검증 역할인 `visual-check` 스킬이 한다. 셋업이 끝나면
"이제 `<키> 검사해줘` 로 검증하세요" 라고 안내하고 끝낸다.

전제: `demo-app` 의 갤러리 구조가 이미 존재한다.
- 레지스트리: `src/gallery/registry.ts`
- 변형 목록: `src/gallery/variants/<키>.ts`
- 타입: `src/gallery/types.ts` 의 `Variant<P>`

## 절차

### 1. 대상 파악
- 사용자가 지정한 컴포넌트 파일을 찾는다(`src/components/<Name>.tsx`). 경로가 모호하면 Glob 으로 찾는다.
- 컴포넌트를 Read 해서 **props 타입**과 의미를 파악한다. props 타입이 `export` 안 돼 있으면
  `export` 로 바꾼다(변형 파일에서 import 해야 하므로).
- **키**를 정한다: 컴포넌트명을 소문자로(`Modal` → `modal`).

### 2. 이미 등록됐는지 확인
```bash
cd demo-app && grep -n "<키>:" src/gallery/registry.ts
```
- 이미 있으면 **"변형 추가" 모드** — 새 컴포넌트 등록은 건너뛰고 4단계(변형 보강)부터 한다.

### 3. 변형 목록 생성
`src/gallery/variants/<키>.ts` 를 만든다. props 를 보고 다음을 섞어 **초안**을 짠다.
- `default` — 가장 평범한 정상 사용 (`expected: 'ok'`)
- 정상 변형 1~2개 — 다른 정당한 props 조합 (`expected: 'ok'`)
- **함정 변형** — props 로 만들 수 있는 깨짐을 노린다:
  - 긴 텍스트 → 넘침/말줄임 (`expected: 'error'` 또는 `'warn'`)
  - optional prop 누락 → 플레이스홀더/빈 영역
  - 극단 값(아주 큰 크기 등) → 비율 깨짐 (`expected: 'warn'`)
  - 낮은 대비 색 조합 → 가독성 (`expected: 'error'`)
  - 글자가 이미지/배경 위에 올라가는 구성 → 묻힘 (`expected: 'error'`)

형식:
```ts
import type { <Name>Props } from '../../components/<Name>'
import type { Variant } from '../types'
export const <키>Variants: Variant<<Name>Props>[] = [
  { id: 'default', label: '기본', expected: 'ok', props: { /* ... */ } },
  { id: 'trap-...', label: '[함정] ...', expected: 'error', props: { /* ... */ } },
]
```

### 4. 레지스트리 등록
`src/gallery/registry.ts` 에 import 와 한 줄을 추가한다.
```ts
import { <Name> } from '../components/<Name>'
import { <키>Variants } from './variants/<키>'
// ... REGISTRY 안에:
  <키>: { label: '<Name>', Comp: <Name>, variants: <키>Variants as unknown as Variant[] },
```

### 5. 타입체크
```bash
cd demo-app && ./node_modules/.bin/tsc -b --noEmit
```
- 에러가 나면 고친다(주로 props 타입 import/스프레드 문제).

### 6. 셋업 완료 보고 (여기서 끝 — 검증은 하지 않는다)
- 무엇을 만들었는지 짧게 보고한다: 등록된 키, 만든 변형 목록(id·label·expected).
- **자동 생성한 변형·정답(expected)은 추정 초안임을 명시**하고, "빼거나 정답을 고칠 변형이 있는지" 검토를 요청한다.
- 마지막에 다음 단계를 안내한다: **"등록 완료. 검증하려면 `<키> 검사해줘` 라고 하세요."**
  (촬영·AI 판정·갤러리·점수는 `visual-check` 가 그때 수행한다.)

## 규칙

- **이 스킬은 셋업만 한다.** 촬영·갤러리 생성·점수·브라우저 열기를 하지 않는다.
- **제품 컴포넌트의 로직은 바꾸지 않는다.** 단 props 타입 `export` 추가처럼 등록에 필요한
  최소 변경은 허용하고, 했으면 사용자에게 알린다.
- 변형·정답은 **초안**이다. 단정하지 말고 사용자 검토를 받는다.
