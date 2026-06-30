import { Gallery } from './gallery/Gallery'

function App() {
  // 가벼운 경로 분기 — 별도 라우터 의존성 없이 /gallery 에서 갤러리를 보여준다.
  if (window.location.pathname.startsWith('/gallery')) {
    return <Gallery />
  }

  return (
    <div
      style={{
        width: '100vw',
        height: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        backgroundColor: '#f8fafc',
      }}
    >
      <a href="/gallery?c=card" style={{ fontSize: 18, color: '#2563eb' }}>
        → 카드 변형 갤러리 보기
      </a>
    </div>
  )
}

export default App
