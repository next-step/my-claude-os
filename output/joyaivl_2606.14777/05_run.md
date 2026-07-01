# 실행 리포트: JoyAI-VL-Interaction 최소 재현 데모

`04_code.md` 분석을 바탕으로, **GPU·8B 가중치·vLLM 없이 실행 가능한** 핵심 로직만 떼어 토이 입력으로 동작을 증명한 데모입니다. 대규모 학습·모델 다운로드는 하지 않았습니다.

## 무엇을 돌렸고, 무엇은 못 돌렸나 (정직성 요약)

- **돌린 것 (실제 실행·검증 완료)** — 저장소에서 GPU가 필요 없는 4개 축:
  - (A) `datasets/convert_data.py :: convert_sample()` — 초당 user/assistant 지도 데이터, **침묵을 일급 라벨(`</silence>`)** 로, 시간마커 `<{s}.0 seconds>` 부여.
  - (B) `services/webui/.../vlm_service.py :: analyze_image()` — 프레임 "인코딩"의 실체 = PIL→JPEG→base64 `data:image/jpeg;base64,…`.
  - (C) `services/webinfer/live_adapter.py :: _resize_image_if_needed(max_pixels=262144)` — 프레임당 시각 토큰 상한(공간 다운스케일).
  - (D) `live_adapter.py :: normalize_model_output()/extract_response_payload()` + `FORCE_SILENCE_BEFORE_QUERY` 단락 — 매초 3지선다(`</silence>`/`</response>`/`</delegation>`)를 **어댑터 문자열 정규화**로 구현. 여기서는 **목(mock) vLLM**이 결정 토큰을 담은 원문을 반환해, 실제 가중치 없이 전 경로를 실행.
- **못 돌린 것 (하드웨어/독점 자산 필수, 04_code.md와 일치)**:
  - 실제 **JoyAI-VL-Interaction-Preview(8B) + Qwen3-VL-4B** vLLM 서빙(포트 7060/8065) — 대용량 VRAM GPU + HF 가중치 필요.
  - **AdaCodec 코덱 자체** — 저장소 미구현(README 로드맵 항목). 재현 불가.
  - **가중 CE 손실(1.5/1/0.4) · GRPO** — 외부 프레임워크(LLaMA-Factory / EasyVideoR1) 필요, 저장소 밖.
  - WebRTC/WebSocket 실시간 루프·백그라운드 codex 위임 실행 — aiortc/av/codex CLI + HTTPS 인증서 필요(배선은 04_code.md에서 정적 분석 완료).

> **충실도 주의**: 업스트림 저장소가 이 워크스페이스에 clone되어 있지 않아, 데모의 함수들은 04_code.md에 문서화된 동작을 **충실히 재구현**한 것입니다(상수 보존: `max_pixels=262144`, `frame_seconds=1.0`, `skip_special_tokens=False`, 3개 결정 마커, `FORCE_SILENCE_BEFORE_QUERY`). 따라서 이 데모는 **문서화된 파이프라인을 토이 데이터로 종단 증명**하며, 독점 8B 체크포인트나 vLLM은 로드하지 않습니다.

## 환경 (OS / Python / 핵심 패키지)

- OS: Windows 10 Education 19045
- Python: **3.10.9** (Anaconda, `C:\Users\wagra\anaconda3\python.exe`)
  - 주의: 시스템 `python`은 Windows Store 스텁(exit 9009)이라 실행 불가 → Anaconda 인터프리터를 절대경로로 사용.
- 핵심 패키지: **Pillow 9.4.0** (유일한 외부 의존성; 나머지는 표준 라이브러리)
- GPU/CUDA/vLLM: 미사용

## 재현 명령 (복붙용 한 블록)

```powershell
# Windows PowerShell. 시스템 python은 스토어 스텁이므로 Anaconda 인터프리터를 직접 지정.
$PY = "C:\Users\wagra\anaconda3\python.exe"
$env:PYTHONIOENCODING = "utf-8"     # 콘솔 UTF-8 (결정 토큰/한글 안전 출력)
cd "C:\Users\wagra\claude\code\output\joyaivl_2606.14777\run"
& $PY -m pip install -r requirements.txt   # Pillow만 (이미 있으면 스킵됨)
& $PY run_demo.py
```

Pillow가 이미 설치되어 있으면 install 단계는 건너뛰어도 됩니다. 실행 소요: **약 1.1초**.

## 실제 실행 로그 (발췌)

```
PART A - convert_sample(): per-second supervision, silence as 1st-class label
  {"role":"user","content":"<0.0 seconds>\n<image>"}
  {"role":"assistant","content":"</silence>"}
  {"role":"user","content":"What is the person doing?\n<1.0 seconds>\n<image>"}
  {"role":"assistant","content":"</response> They are picking up a red cup."}
  ... (sec2 silence, sec3 response, sec4 silence)
  [check] assistant turns: 3 silence + 2 response = 5  (expected 5s -> 3 sil + 2 resp)
  [ok] time markers present, silence is an explicit assistant label.

PART B+C - frame encoding (JPEG->base64) + max_pixels resize cap
  big frame  1920x1080 (2073600 px) -> encoded at (682, 384) (261888 px),
             data_url prefix='data:image/jpeg;base64,/9j/4AA', b64_len=88628
  small frame 320x240 (76800 px) -> encoded at (320, 240) (unchanged), b64_len=7472
  [ok] big frame capped to <= 262144 px; small passed through; both as data URLs.

PART D - per-second decision loop (MOCK vLLM)
  sec | query? | called |   decision   | payload/delegation
  ----------------------------------------------------------
    0 |   no   |   no   |  </silence>  |
    1 |  yes   |  yes   | </response>  | say: 'They are picking up a red cup.'
    2 |   no   |   no   |  </silence>  |
    3 |  yes   |  yes   |  </silence>  |
    4 |  yes   |  yes   |  delegation  | delegate: 'Summarize the 2h recording and draft an email'
  [ok] FORCE_SILENCE_BEFORE_QUERY short-circuits silent seconds without calling the model
  [ok] response / silence / delegation all parsed from raw decision tokens.

DEMO COMPLETE - all assertions passed   (EXIT=0, ELAPSED_SEC=1.12)
```

## 결과 해석 (출력이 논문 동작과 일치하는가)

- **침묵이 일급 라벨**: 응답이 없는 초(0·2·4)가 빈칸이 아니라 `{"role":"assistant","content":"</silence>"}`로 채워짐 — 논문 §의 "silence as a first-class label" 및 04_code.md 표 #14와 정확히 일치. 시간마커 `<s.0 seconds>`(타이밍 감독) + `</response> {text}`(내용 감독)의 2축 구조도 재현.
- **토큰 예산 관리(AdaCodec 대체 장치)**: 1920×1080(207만 px) 프레임이 `max_pixels=262144` 제약으로 682×384(≈26.2만 px)로 다운스케일되고, 작은 프레임은 무변경 통과 — 04_code.md 표 #6, Part0 §3(c)와 일치. 코덱이 아니라 공간 다운스케일로 프레임당 ViT 토큰 수를 상한.
- **프레임 인코딩 실체**: 모든 프레임이 예측/잔차 없이 온전한 JPEG→base64 `data:image/jpeg;base64,…`로 방출(`/9j/4AA…` = JPEG SOI 매직) — Part0 §3(b), 표 #7과 일치. P-토큰 없음을 코드 경로로 재확인.
- **매초 3지선다 + 강제 침묵 단락**: 질의가 없는 초(0·2)는 `FORCE_SILENCE_BEFORE_QUERY`로 **모델을 호출하지 않고** 즉시 `</silence>`(called=no) — 표 #9, Part1 §3-B와 일치(추론 비용 절감). 질의가 있어도 모델이 `</silence>`를 고를 수 있음(sec3). 위임은 `</response> … </delegation> <question>…</question>` 2단 포맷에서 질문을 추출(sec4) — 표 #3/#11, Part1 §3-A와 일치.
- 요약하면, GPU가 필요 없는 논문 메커니즘 4종이 문서화된 대로 정확히 동작함을 실행 로그로 증명. 모델 품질(무엇을 말할지)은 8B 가중치의 몫이라 본 데모 범위 밖.

## 알려진 이슈 / 다음 단계 (전체 스택으로 확장하려면)

1. **실제 모델 서빙**: `install/download-models.sh --all`로 `JoyAI-VL-Interaction-Preview`(8B) + `Qwen3-VL-4B` 확보 → `services/webinfer/scripts/start_all_models.sh`(vLLM :7060/:8065) → `start_adapter.sh`(:8070). 대용량 VRAM GPU(단일 고사양 또는 `TENSOR_PARALLEL_SIZE` 텐서병렬) 필수. 본 데모의 `mock_main_model`을 `AsyncOpenAI`(`main_api_base=http://127.0.0.1:7060/v1`) 실호출로 교체하면 동일 파이프라인이 실제 결정을 생성.
2. **실시간 루프**: `services/scripts/run.sh minimal` + `generate_cert.sh`(HTTPS) → `https://127.0.0.1:8099`에서 웹캠으로 WebRTC 1Hz 루프 확인. `aiortc`/`av` 설치 필요.
3. **위임 실행**: `all` 모드 + `codex` CLI + `CODEX_HOME` → `POST :8079/v1/solve`로 백그라운드 태스크 단위 검증.
4. **학습(외부 프레임워크)**: (A)의 변환 JSON을 LLaMA-Factory SFT(가중 CE `w_response=1.5/w_silence_first=1/w_silence_repeated=0.4`는 여기서 부여) → EasyVideoR1 GRPO. 저장소 밖, 별도 설치·대규모 데이터·GPU 필요(본 스킬 범위 밖).
5. **AdaCodec**: 저장소 미구현(로드맵). 논문의 P-토큰/참조프레임/모션·잔차 인코더가 공개되면 그때 재현 가능.

## 산출물 경로

- 데모 코드: `C:/Users/wagra/claude/code/output/joyaivl_2606.14777/run/run_demo.py`
- 의존성: `C:/Users/wagra/claude/code/output/joyaivl_2606.14777/run/requirements.txt`
- 본 리포트: `C:/Users/wagra/claude/code/output/joyaivl_2606.14777/05_run.md`
