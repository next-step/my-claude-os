/**
 * 시각 검증의 대상이 되는 **공통 컴포넌트**.
 *
 * 한 번 바꾸면 props 가 다른 여러 변형(사용처)이 제각각 영향을 받는다.
 * 그 변형들을 `/gallery` 화면에 모아두고(→ src/gallery/variants.ts),
 * 스크린샷 · 코드 측정 · AI 눈으로 검증한다.
 *
 * 일부 props 는 "일부러 깨지는" 함정을 만든다 — 코드가 못 잡는 시각 깨짐을
 * AI 눈이 식별하는지 검증하기 위한 것이다.
 *  - textOnImage: 글자를 이미지 위에 올린다 → 밝은 사진 위 흰 글자가 묻힐 수 있다(코드 대비 측정 불가).
 *  - titleFontSize: 제목을 키운다 → 칸 비율에 안 맞아 어색해질 수 있다.
 */
export type CardProps = {
  title: string
  description?: string
  imageUrl?: string
  badge?: string
  theme?: 'light' | 'dark'
  disabled?: boolean
  /** 글자를 이미지 위에 겹쳐 올린다 (이미지 위 글자 묻힘 함정) */
  textOnImage?: boolean
  /** 제목 폰트 크기(px). 기본 16. 키우면 비율이 깨질 수 있다 */
  titleFontSize?: number
  /**
   * 카드 배경색을 덮어쓴다 (라이트 테마 기준).
   * 미묘하게 어긋난 색 함정 — 안 깨졌지만 살짝 어색한 톤이 AI 눈에 잡히는지 보는 용도.
   */
  bgTint?: string
  /**
   * 상단 이미지/플레이스홀더 블록을 숨긴다.
   * 프레임 안에 비교 기준이 될 다른 색 요소를 제거해, 어긋난 색이 "홀로" 있을 때
   * AI 눈이 못 잡는 진짜 사각지대를 만들기 위한 용도.
   */
  hideMedia?: boolean
}

export function Card({
  title,
  description,
  imageUrl,
  badge,
  theme = 'light',
  disabled = false,
  textOnImage = false,
  titleFontSize = 16,
  bgTint,
  hideMedia = false,
}: CardProps) {
  const isDark = theme === 'dark'

  // 이미지 위에 글자를 올리는 함정 모드 — 사진 위에 흰 글자를 겹쳐 둔다.
  if (textOnImage && imageUrl) {
    return (
      <div
        style={{
          width: 240,
          height: 280,
          boxSizing: 'border-box',
          position: 'relative',
          borderRadius: 11,
          overflow: 'hidden',
          border: '1px solid #e2e8f0',
          boxShadow: '0 1px 3px rgba(0,0,0,0.08)',
        }}
      >
        <img
          src={imageUrl}
          alt=""
          style={{ width: '100%', height: '100%', objectFit: 'cover', display: 'block' }}
        />
        <div
          style={{
            position: 'absolute',
            inset: 0,
            padding: 16,
            display: 'flex',
            flexDirection: 'column',
            justifyContent: 'flex-end',
            gap: 6,
          }}
        >
          {badge && (
            <span
              style={{
                alignSelf: 'flex-start',
                fontSize: 12,
                fontWeight: 600,
                padding: '2px 8px',
                borderRadius: 999,
                color: '#ffffff', // 흰 뱃지 글자 — 밝은 사진 위에서 묻힐 수 있다
              }}
            >
              {badge}
            </span>
          )}
          <h3
            style={{
              margin: 0,
              fontSize: titleFontSize,
              fontWeight: 700,
              lineHeight: 1.3,
              color: '#ffffff', // 흰 제목 — 배경 사진이 밝으면 안 읽힌다 (코드는 못 잡음)
            }}
          >
            {title}
          </h3>
          {description && (
            <p style={{ margin: 0, fontSize: 14, lineHeight: 1.5, color: '#ffffff' }}>
              {description}
            </p>
          )}
        </div>
      </div>
    )
  }

  return (
    <div
      style={{
        width: 240,
        height: 280,
        boxSizing: 'border-box',
        display: 'flex',
        flexDirection: 'column',
        padding: 16,
        borderRadius: 12,
        border: isDark ? '1px solid #334155' : '1px solid #e5e7eb',
        backgroundColor: bgTint ?? (isDark ? '#1e2937' : '#ffffff'),
        color: isDark ? '#f1f5f9' : '#0f172a',
        opacity: disabled ? 0.5 : 1,
        overflow: 'hidden', // 넘친 내용을 잘라낸다 (잘림 감지의 핵심)
        boxShadow: '0 1px 3px rgba(0,0,0,0.08)',
      }}
    >
      {!hideMedia && (imageUrl ? (
        <img
          src={imageUrl}
          alt=""
          style={{
            width: '100%',
            height: 120,
            objectFit: 'cover',
            borderRadius: 8,
            flexShrink: 0,
          }}
        />
      ) : (
        <div
          style={{
            width: '100%',
            height: 120,
            borderRadius: 8,
            flexShrink: 0,
            backgroundColor: isDark ? '#334155' : '#f1f5f9',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            color: isDark ? '#64748b' : '#94a3b8',
            fontSize: 13,
          }}
        >
          이미지 없음
        </div>
      ))}

      <div style={{ marginTop: 12, display: 'flex', alignItems: 'center', gap: 8 }}>
        <h3
          style={{
            margin: 0,
            fontSize: titleFontSize, // 키우면 칸 비율이 깨질 수 있다
            fontWeight: 600,
            lineHeight: 1.3,
            whiteSpace: 'nowrap', // 한 줄 고정 — 길면 말줄임(의도된 처리)
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            flex: 1,
          }}
        >
          {title}
        </h3>
        {badge && (
          <span
            style={{
              flexShrink: 0,
              fontSize: 12,
              fontWeight: 600,
              padding: '2px 8px',
              borderRadius: 999,
              backgroundColor: isDark ? '#0369a1' : '#e0f2fe',
              color: isDark ? '#e0f2fe' : '#0369a1',
            }}
          >
            {badge}
          </span>
        )}
      </div>

      {description && (
        <p
          style={{
            margin: '8px 0 0',
            fontSize: 14,
            lineHeight: 1.5,
            color: isDark ? '#94a3b8' : '#6b7280',
          }}
        >
          {description}
        </p>
      )}
    </div>
  )
}
