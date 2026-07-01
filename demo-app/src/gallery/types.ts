/** 어떤 컴포넌트든 공통으로 쓰는 변형 정의. */
export type Variant<P = Record<string, unknown>> = {
  id: string
  label: string
  /** 이 변형의 "정답"(설계 의도). AI/코드 판정을 채점하는 기준. */
  expected: 'ok' | 'warn' | 'error'
  props: P
}
