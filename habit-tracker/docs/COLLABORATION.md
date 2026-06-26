# 에이전트 협업 가이드

habit-tracker 프로젝트는 4개의 전문 에이전트가 협력해 개발한다.
각 에이전트는 Claude Code 스킬로 호출하며, 정해진 산출물을 통해 서로 소통한다.

---

## 에이전트 목록

| 에이전트 | 호출 키워드 | 전문 영역 |
|---------|-----------|---------|
| **Planner** | "기획자로 ~해줘" | 정책 관리, 기능 기획, 게이미피케이션 설계, PRD 작성 |
| **Designer** | "디자이너로 ~해줘" | UI/UX 설계, 디자인 시스템, 화면 명세 |
| **Backend Dev** | "백엔드 개발자로 ~해줘" | Spring Boot, JPA, TDD, API 설계 |
| **Frontend Dev** | "프론트 개발자로 ~해줘" | Thymeleaf, HTML/CSS, 반응형 UI 구현 |
| **QA Engineer** | "QA해줘" | 테스트 케이스, 버그 리포트, 품질 검토 |

---

## 협업 플로우

```
[사용자 요구사항]
       │
       ▼
  ┌──────────┐
  │  Planner │  → PRD 작성 + POLICY.md 관리
  └────┬─────┘       (docs/planning/)
       │ 디자인 브리프 전달
       ▼
  ┌─────────┐
  │ Designer │  → 화면 명세서 작성
  └────┬─────┘       (docs/design/specs/)
       │
       ├──────────────────────────┐
       ▼                          ▼
  ┌──────────────┐         ┌─────────────┐
  │ Backend Dev  │         │Frontend Dev │
  │ API 설계·구현 │         │ UI 구현     │
  └──────┬───────┘         └──────┬──────┘
         │                        │
         │  API 계약서             │ 구현 완료 통보
         │  (docs/api/)           │
         └────────────┬───────────┘
                      ▼
               ┌─────────────┐
               │ QA Engineer │  → 테스트 케이스 + 버그 리포트
               └──────┬──────┘
                      │
                      ▼
               [사용자에게 결과 보고]
```

> **정책 변경 시**: Planner가 POLICY.md를 수정하고 영향받는 에이전트에게 직접 코드 반영을 지시한다.

---

## 산출물 위치

```
habit-tracker/docs/
├── planning/
│   ├── POLICY.md                 # 기획자: 앱 정책서 (Living Document)
│   └── prd-[기능명].md           # 기획자: 기능별 PRD
├── design/
│   ├── design-system.md          # 디자이너: 공용 디자인 토큰 (색상, 타이포, 간격)
│   └── specs/                    # 디자이너: 기능별 화면 명세서
│       └── [기능명].md
├── api/
│   └── endpoints.md              # 백엔드: API 계약서 (URL, 모델 속성, 응답)
└── qa/
    ├── test-cases/               # QA: 기능별 테스트 케이스
    │   └── [기능명].md
    └── bug-reports/              # QA: 기능별 버그 리포트
        └── [기능명]-bugs.md
```

---

## 에이전트 간 입출력 계약

### Designer → 백엔드/프론트/QA
**파일**: `docs/design/specs/[기능명].md`
**포함 내용**:
- 화면 목적 (사용자가 달성하려는 것)
- 텍스트 와이어프레임 (레이아웃 구조)
- 컴포넌트 상태 정의 (기본/완료/비활성)
- UX 원칙 체크리스트
- 반응형 규칙

### Backend Dev → 프론트/QA
**파일**: `docs/api/endpoints.md`
**포함 내용**:
- URL 목록과 HTTP 메서드
- Thymeleaf 모델 속성명 (Model.addAttribute 키)
- 폼 입력 필드명과 유효성 규칙
- 리다이렉트 경로
- 예외 처리 방식

### Frontend Dev → QA
**통보 방식**: 채팅으로 구현 완료 및 테스트 포인트 전달
**포함 내용**:
- 구현된 템플릿 파일 경로 목록
- 특별히 확인이 필요한 인터랙션

### QA → 백엔드/프론트
**파일**: `docs/qa/bug-reports/[기능명]-bugs.md`
**포함 내용**:
- 심각도별 버그 목록
- 재현 단계
- 추정 원인 (파일:라인 포함)
- 담당 에이전트 명시

---

## 기능 개발 예시 순서

```
1. "디자이너로 루틴 관리 화면 명세 작성해줘"
   → docs/design/specs/routine-management.md 생성

2. "백엔드 개발자로 루틴 관리 API 구현해줘"
   → 서비스/리포지토리/컨트롤러 + 테스트 + docs/api/endpoints.md

3. "프론트 개발자로 루틴 관리 화면 구현해줘"
   → templates/routines/*.html + style.css

4. "루틴 관리 기능 QA해줘"
   → docs/qa/test-cases/routine-management.md
   → docs/qa/bug-reports/routine-management-bugs.md
```

---

## 에이전트 없이 빠른 작업할 때

단순 수정이나 명확한 작업은 에이전트 없이 직접 진행해도 된다.
에이전트는 **기능 단위의 설계·구현·검증**처럼 역할 전문성이 필요할 때 사용한다.
