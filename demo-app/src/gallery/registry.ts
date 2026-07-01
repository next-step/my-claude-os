import type { ComponentType } from 'react'
import { Card } from '../components/Card'
import type { Variant } from './types'
import { cardVariants } from './variants/card'

/**
 * 검증 대상 컴포넌트 레지스트리 — "진실의 원천".
 * 새 컴포넌트를 추가하려면 변형 목록을 만들고 여기 한 줄만 등록하면 된다.
 * 그러면 갤러리 화면·촬영·측정·채점이 모두 자동으로 그 컴포넌트에 적용된다.
 */
export type RegistryEntry = {
  label: string
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  Comp: ComponentType<any>
  variants: Variant[]
}

export const REGISTRY: Record<string, RegistryEntry> = {
  card: { label: 'Card', Comp: Card, variants: cardVariants as unknown as Variant[] },
}

export const REGISTRY_KEYS = Object.keys(REGISTRY)
