import type { CardProps } from '../../components/Card'
import type { Variant } from '../types'
import hero from '../../assets/hero.png'

/** 공통 Card 컴포넌트의 변형 목록 (정상 + 함정). */
export const cardVariants: Variant<CardProps>[] = [
  {
    id: 'default',
    label: '기본',
    expected: 'ok',
    props: {
      title: '여행 패키지',
      description: '제주 2박 3일, 항공·숙소 포함.',
      imageUrl: hero,
      badge: 'NEW',
    },
  },
  {
    id: 'long-title',
    label: '긴 제목',
    expected: 'ok',
    props: {
      title: '아주 길고 긴 제목이 들어가면 한 줄을 넘겨 잘리는지 확인하는 변형입니다',
      description: '제목이 길 때 말줄임/넘침이 어떻게 처리되는지 본다.',
      imageUrl: hero,
      badge: 'HOT',
    },
  },
  {
    id: 'no-image',
    label: '이미지 없음',
    expected: 'ok',
    props: {
      title: '이미지 없는 카드',
      description: 'imageUrl 이 없을 때 플레이스홀더가 뜨는지 본다.',
      badge: 'SALE',
    },
  },
  {
    id: 'long-description',
    label: '긴 본문',
    expected: 'error',
    props: {
      title: '본문이 긴 카드',
      description:
        '본문 텍스트가 카드 높이를 넘어설 만큼 길어지면 아래쪽이 잘리는지 확인하는 변형이다. 이 문장은 일부러 길게 써서 카드 하단 넘침을 유도한다. 더 길게, 더 길게, 정말 더 길게 늘여서 바닥을 넘긴다.',
      imageUrl: hero,
    },
  },
  {
    id: 'dark',
    label: '다크 테마',
    expected: 'ok',
    props: {
      title: '다크 카드',
      description: '어두운 배경에서 텍스트 대비가 충분한지 본다.',
      imageUrl: hero,
      badge: 'DARK',
      theme: 'dark',
    },
  },
  {
    id: 'disabled',
    label: '비활성',
    expected: 'warn',
    props: {
      title: '비활성 카드',
      description: 'opacity 가 낮을 때 가독성/대비를 본다.',
      imageUrl: hero,
      badge: 'OFF',
      disabled: true,
    },
  },

  // ── 함정 변형 ── 코드가 못 잡는 시각 깨짐을 AI 눈이 식별하는지 검증한다.
  {
    id: 'trap-text-on-image',
    label: '[함정] 이미지 위 글자',
    expected: 'error',
    props: {
      title: '사진 위 흰 글자',
      description: '밝은 사진 위라 글자가 묻혀 안 읽힐 수 있다.',
      imageUrl: hero,
      badge: 'NEW',
      textOnImage: true,
    },
  },
  // ── 미묘한 색 어긋남(케이스 #3) ── 안 깨졌지만 살짝 다른 톤. 같은 내용 두 장을
  //    나란히 두고, A=정상 흰색 / B=살짝 따뜻한 오프화이트. AI 눈이 B를 어색하다고 잡나?
  {
    id: 'tone-a',
    label: '요금제 카드 A',
    expected: 'ok',
    props: {
      title: '프로 플랜',
      description: '팀 협업, 무제한 프로젝트, 우선 지원.',
      badge: 'PRO',
    },
  },
  {
    id: 'tone-b',
    label: '요금제 카드 B',
    expected: 'warn', // 흰색이어야 할 카드가 크림으로 어긋남 → "잡아야 정답"
    props: {
      title: '프로 플랜',
      description: '팀 협업, 무제한 프로젝트, 우선 지원.',
      badge: 'PRO',
      bgTint: '#f5f1e8', // 흰색이어야 할 카드가 미묘하게 크림빛 — 안 깨졌지만 어색
    },
  },

  // ── 진짜 사각지대(케이스 #3 바닥) ── 미디어·배지·이웃 없이 어긋난 색만 홀로.
  //    프레임 안에 비교할 다른 색이 없어 AI가 못 잡을 것으로 예상.
  //    C=미묘한 크림 / D=정상 흰색(대조군). 각각 단독 블라인드로 판정.
  {
    id: 'tone-c',
    label: '고립 카드 C',
    expected: 'warn', // 같은 크림 어긋남 → "잡아야 정답"인데 고립돼서 AI가 못 잡음
    props: {
      title: '프로 플랜',
      description: '팀 협업, 무제한 프로젝트, 우선 지원.',
      hideMedia: true,
      bgTint: '#f5f1e8', // 흰색이어야 할 카드가 미묘하게 크림빛 — 비교 대상 없음
    },
  },
  {
    id: 'tone-d',
    label: '고립 카드 D',
    expected: 'ok',
    props: {
      title: '프로 플랜',
      description: '팀 협업, 무제한 프로젝트, 우선 지원.',
      hideMedia: true,
    },
  },

  {
    id: 'trap-oversized-title',
    label: '[함정] 큰 제목',
    expected: 'warn',
    props: {
      title: '제목이 너무 큼',
      description: '제목 폰트가 칸 비율에 안 맞아 어색하다.',
      imageUrl: hero,
      badge: 'HOT',
      titleFontSize: 30,
    },
  },
]
