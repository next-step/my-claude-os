---
name: code-run
description: Make the paper's implementation immediately runnable — produce a minimal reproducible setup (env/deps, a runnable script or notebook, sample input) and actually run it to confirm it works. Use after /code when the user wants to execute the implementation right away (코드실행).
---

# /code-run — 코드 실행 스킬 (코드실행!)

`/code` 분석 결과를 받아 **바로 실행 가능한 최소 재현 환경**을 만들고, 실제로 돌려서 동작을 확인합니다.

> 슬래시 이름: `/code-run` (원 요청의 `/코드실행!`에 해당).

## 입력
- `output/04_code.md`(필수). 없으면 먼저 `/code` 실행.

## 절차
1. **계획**: `04_code.md`의 의존성/진입점/최소 재현 경로를 읽고 실행 계획 수립.
2. **격리 환경**: 가능한 한 가벼운 셋업을 만든다.
   - Python이면 `output/run/` 아래에 `requirements.txt` + `run_demo.py`(또는 `.ipynb`) 생성.
   - 무거운 학습 대신 **토이 입력으로 1-step 추론/순전파**만 돌리는 데모를 우선한다.
3. **설치**: `pip install -r requirements.txt`(가상환경 권장). 설치 실패 시 버전 고정/대안 패키지로 복구.
4. **실행**: 데모 스크립트를 실제로 실행하고 출력(텐서 shape, 손실 값, 샘플 결과)을 캡처.
5. **기록**: 실행 명령·출력·소요시간을 `output/05_run.md`에 정리. 재현 명령을 한 블록으로 제공.

## 안전 / 현실성 규칙
- 대규모 데이터셋 다운로드·장시간 GPU 학습은 기본적으로 **하지 않는다**. 더미/소형 입력으로 동작만 증명.
- 네트워크·설치는 필요한 최소만. 사용자 승인이 필요한 무거운 작업은 먼저 알린다.
- 실행 불가(예: 독점 데이터/하드웨어 필수)면 그 사유와 함께 "무엇을 갖추면 실행 가능한지"를 명시.

## 출력 (`output/05_run.md`)
```markdown
# 실행 리포트: <제목>
## 환경 (OS / Python / 핵심 패키지 버전)
## 재현 명령 (복붙용 한 블록)
## 실제 실행 로그 (발췌)
## 결과 해석 (출력이 논문 동작과 일치하는가)
## 알려진 이슈 / 다음 단계 (전체 학습으로 확장하려면)
```

## 품질 기준
- "돌렸다"는 주장은 실제 실행 로그로 뒷받침. 못 돌렸으면 솔직히 명시.
- 재현 명령은 그대로 복사해 실행 가능해야 한다.
- 완료 후 `output/05_run.md`와 `output/run/` 경로 보고.
