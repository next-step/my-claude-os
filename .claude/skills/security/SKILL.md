---
name: security
description: |
  security-auditor 에이전트로 보안 취약점을 점검하고, 발견된 취약점을 백엔드/프론트 개발자에게 수정 지시하는 스킬.

  다음 표현에 반드시 사용한다:
  - "/security" (인자 없음) — 전체 코드베이스 보안 점검
  - "/security [범위]" — 특정 기능/파일 보안 점검
  - "보안 점검해줘", "보안 취약점 찾아줘", "security audit 해줘"
  - "보안 취약점 수정해줘", "보안 패치해줘"
---

# Security 스킬

`security-auditor` 에이전트로 취약점을 발굴하고,
발견된 취약점을 `backend-dev` / `frontend-dev` 에이전트에게 분배해 수정까지 완료한다.

---

## 모드 판별

| 상황 | 범위 |
|------|------|
| `/security` (인자 없음) | 전체 코드베이스 |
| `/security XP 시스템` | 해당 기능 관련 파일만 |
| `/security RoutineController` | 해당 파일만 |

---

## Step 1: security-auditor 에이전트 실행

```
아래 범위에 대해 보안 감사를 진행해줘.

점검 범위: {인자 / 또는 "habit-tracker 전체 코드베이스"}

작업 순서:
1. 점검 범위에 해당하는 파일을 탐색
2. OWASP Top 10 기준으로 취약점을 분석
3. habit-tracker/docs/security/[범위]-security-report.md 로 리포트 작성
4. 완료 보고: CRITICAL/HIGH/MEDIUM/LOW 건수 + Backend/Frontend 분류
```

---

## Step 2: 결과 파싱

security-auditor 보고에서 아래를 추출한다:

- **Backend 수정 필요 목록** (VUL 번호 + 파일경로 + 수정 방향)
- **Frontend 수정 필요 목록** (VUL 번호 + 파일경로 + 수정 방향)
- **CRITICAL/HIGH 건수** → 0건이면 Step 3~4 생략하고 Step 5로 바로 이동

---

## Step 3: backend-dev 에이전트 실행 (Backend 취약점이 있을 때만)

```
보안 감사 결과 아래 취약점이 발견됐어. 수정해줘.

리포트: habit-tracker/docs/security/[범위]-security-report.md

수정 대상 (Backend):
{Step 2에서 추출한 Backend 취약점 목록 — VUL 번호, 파일경로, 수정 방향 포함}

수정 원칙:
- CRITICAL → HIGH → MEDIUM → LOW 순으로 우선 처리
- 보안 수정은 기존 테스트를 깨뜨리지 않아야 함
- 수정 후 영향받는 테스트가 있으면 함께 업데이트
- 완료 후 수정한 파일 목록과 각 VUL 번호를 보고해줘
```

---

## Step 4: frontend-dev 에이전트 실행 (Frontend 취약점이 있을 때만)

```
보안 감사 결과 아래 취약점이 발견됐어. 수정해줘.

리포트: habit-tracker/docs/security/[범위]-security-report.md

수정 대상 (Frontend):
{Step 2에서 추출한 Frontend 취약점 목록 — VUL 번호, 파일경로, 수정 방향 포함}

수정 원칙:
- th:utext → th:text 교체, 인라인 스크립트 제거 등 Thymeleaf 보안 관행 준수
- CRITICAL → HIGH 순으로 우선 처리
- 완료 후 수정한 파일 목록과 각 VUL 번호를 보고해줘
```

---

## Step 5: 최종 보고

```
✅ Security 스킬 완료: [범위]

─────────────────────────────
🔒 보안 감사 결과
─────────────────────────────
리포트: habit-tracker/docs/security/[범위]-security-report.md
CRITICAL: N건 | HIGH: N건 | MEDIUM: N건 | LOW: N건

─────────────────────────────
⚙️  Backend 수정
─────────────────────────────
[수정된 파일 목록 + VUL 번호 / 취약점 없음]

─────────────────────────────
🖥️  Frontend 수정
─────────────────────────────
[수정된 파일 목록 + VUL 번호 / 취약점 없음]

─────────────────────────────
⚠️  미수정 항목 (MEDIUM/LOW)
─────────────────────────────
[수정하지 않은 취약점 목록 — 백로그 처리 권장]
```

---

## 실행 원칙

- Step 3(Backend)와 Step 4(Frontend)는 **병렬 실행 가능** — 서로 다른 파일을 수정하기 때문
- CRITICAL이 있으면 수정 완료 전까지 최종 보고를 내지 않는다
- 취약점이 전혀 없는 경우 → "보안 취약점 없음. 현재 코드는 안전합니다" 보고 후 종료
- 수정 후 재감사가 필요하면 사용자에게 `/security` 재실행을 권장한다
