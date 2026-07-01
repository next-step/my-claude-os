import { REGISTRY, REGISTRY_KEYS } from './registry'

/**
 * 레지스트리에 등록된 컴포넌트의 변형들을 한 화면에 모아 렌더한다 (OS.md 1·6단계).
 *
 * 대상은 `?c=<키>` 로 고른다. 예: /gallery?c=card
 * 각 변형은 [data-variant-id]·[data-expected] 로 감싸 둔다 — 촬영 스크립트가 이 속성으로
 * 컴포넌트 종류와 무관하게 변형 하나하나를 찾아 찍는다.
 */
export function Gallery() {
  const param = new URLSearchParams(window.location.search).get('c')
  const key = param && REGISTRY[param] ? param : REGISTRY_KEYS[0]
  const entry = REGISTRY[key]
  const { Comp, variants } = entry

  return (
    <div
      style={{
        minHeight: '100vh',
        padding: 32,
        backgroundColor: '#f8fafc',
        fontFamily: 'system-ui, sans-serif',
      }}
    >
      <h1 style={{ margin: '0 0 4px', fontSize: 22, color: '#0f172a' }}>
        {entry.label} 변형 갤러리
      </h1>
      <nav style={{ margin: '0 0 24px', display: 'flex', gap: 12, fontSize: 14 }}>
        {REGISTRY_KEYS.map((k) => (
          <a
            key={k}
            href={`/gallery?c=${k}`}
            style={{
              color: k === key ? '#2563eb' : '#94a3b8',
              fontWeight: k === key ? 700 : 400,
              textDecoration: 'none',
            }}
          >
            {REGISTRY[k].label}
          </a>
        ))}
      </nav>

      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 24, alignItems: 'flex-start' }}>
        {variants.map((v) => (
          <div
            key={v.id}
            data-variant-id={v.id}
            data-expected={v.expected}
            style={{ display: 'flex', flexDirection: 'column', gap: 8 }}
          >
            <Comp {...v.props} />
            <span style={{ fontSize: 12, color: '#94a3b8' }}>{v.label}</span>
          </div>
        ))}
      </div>
    </div>
  )
}
