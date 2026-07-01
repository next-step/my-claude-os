# 구현 코드 분석: JoyAI-VL-Interaction (통합)

> 이 문서는 논문 JoyAI-VL-Interaction의 공개 구현을 **세 하위시스템**으로 나누어 분석한 뒤 하나로 병합한 것입니다.
> - **Part 0 — AdaCodec 시각 코덱 + 비전-언어 베이스 모델**: 프레임이 어떻게 토큰으로 인코딩되어 베이스 VLM에 들어가고, 그 모델이 어떻게 서빙되는가.
> - **Part 1 — 실시간 per-second 의사결정 루프 + 학습 데이터/프레임워크 축**: 매초 침묵/응답/위임 결정과 학습 파이프라인의 데이터 변환.
> - **Part 2 — 배포 가능한 종단 서빙 스택**: vLLM 서빙, WebRTC/WebSocket 실시간 루프, 계층적 기억, 백그라운드 위임, 시각화 UI.

- **저장소**: https://github.com/jd-opensource/JoyAI-VL-Interaction (**공식** — JD.com `jd-opensource` 조직, CC BY 4.0 / 코드 Apache-2.0)
  - 분석 시점 clone: 기본 브랜치, 최신 커밋 트리 `9d07596`. 무게 파일(`JoyAI-VL-Interaction-Reportv1.pdf`)은 제외하고 소스만 확인.
- **언어 / 핵심 프레임워크**: Python 3.12+ / **vLLM**(OpenAI-호환 서빙, prefix caching) / `aiohttp`(webinfer 어댑터·WebSocket) / `aiortc`(WebRTC 수신) / `Pillow`(프레임 인코딩) / `openai`·`AsyncOpenAI` SDK(클라이언트) / `FastAPI`+`codex` CLI(백그라운드 위임). 학습은 외부 프레임워크 **LLaMA-Factory**(SFT) + **EasyVideoR1**(RL/GRPO) 의존.
- **실행 진입점**:
  - 전체 스택 오케스트레이션: `services/scripts/run.sh` (`minimal` | `all` 모드) → WebUI 포그라운드, `https://127.0.0.1:8099`
  - 베이스 VLM 서빙: `services/webinfer/scripts/start_model.sh` → `vllm.entrypoints.openai.api_server` (포트 7060)
  - 프레임→토큰 어댑터 서버: `services/webinfer/live_adapter.py` (포트 8070)
  - 프레임 캡처/인코딩 클라이언트: `services/webui/src/joy_interaction_webui/video_processor.py:VideoProcessorTrack.recv()`
  - 학습 데이터 파이프라인: `datasets/convert_data.py` (`convert_sample(...)`)

---

## ⚠️ 핵심 발견 — AdaCodec은 이 릴리스에 **미구현**(로드맵 항목)

논문(§4)은 AdaCodec을 "참조 프레임=전체 ViT 토큰 / 예측 프레임=모션·잔차 기반 P-토큰(약 16토큰)"으로 압축하는 예측적 코덱으로 설명하지만, **공개된 코드에는 P-토큰·참조프레임·모션/잔차 인코더가 존재하지 않습니다.** 저장소 전체에서 `AdaCodec`/`P-token`/`reference frame`/`residual`/`motion`을 검색하면 실제 구현이 아니라 **README 로드맵의 미체크 항목** 한 줄만 나옵니다:

```
README.md:188
- [ ] **Codec version** — model variant with the predictive video codec (AdaCodec)
      for lower token cost over long streams
```
그리고 소개문(README.md:96)에도 "A predictive video codec (AdaCodec) …" 라고 **개념만** 언급됩니다.

**따라서 실제로 코드로 존재하는 것은 다음과 같습니다.**
1. **베이스 VLM 자체** — `JoyAI-VL-8B` 위에 학습된 `JoyAI-VL-Interaction-Preview`를 표준 vLLM으로 서빙(README.md:96 "We build it on JoyAI-VL-8B").
2. **AdaCodec을 대체하는 배포용 토큰-예산 관리** — 코덱 대신 (a) 1 Hz 프레임 샘플링, (b) `max_pixels` 공간 다운스케일, (c) 청크/요약 계층 메모리, (d) vLLM prefix caching으로 "장시간 스트림에서 토큰 폭발"을 억제합니다. 즉 논문 AdaCodec의 *목표*(장시간 토큰 예산 유지)를 코덱이 아닌 **시스템 레벨 장치**로 달성합니다.

이 점을 명시하는 것이 이 분석의 가장 중요한 결론입니다. 이하 "논문↔코드 매핑"은 이 사실 위에서 작성됩니다.

---

## 통합 논문 ↔ 코드 매핑 표

아래 표는 세 하위시스템의 매핑을 하나로 통합한 것입니다. `Part` 열은 상세 분석이 실린 하위시스템을 가리킵니다(P0=베이스모델/코덱, P1=결정루프/학습, P2=서빙스택).

| # | 논문 개념 | 코드 위치 (파일:함수/클래스/상수) | 설명 | Part |
|---|---|---|---|---|
| 1 | 베이스: JoyAI-VL 1.0 (Qwen3-8B LM + Qwen3-VL ViT), 8B | `services/webinfer/scripts/start_model.sh` (`MODEL_PATH=…/JoyAI-VL-Interaction-Preview`, `SERVED_MODEL_NAME`) | 학습된 8B 가중치를 vLLM `openai.api_server`로 서빙. ViT 토큰화는 모델 가중치/vLLM 내부에서 처리되며 저장소 코드에 별도 ViT 정의는 없음(추정: 모델 리포에 포함). | P0 |
| 2 | vLLM 네이티브 서빙 + prefix caching (서브초 지연) | `webinfer/scripts/start_model.sh` (`--enable-prefix-caching --enable-chunked-prefill`, `--max-model-len 131072`, `--gpu-memory-utilization 0.9`) | 메인 8B VLM을 표준 vLLM OpenAI API로 :7060 서빙. 프리픽스 캐싱으로 2시간+ 스트림에서도 종단 지연 억제. | P0·P2 |
| 3 | 매 타임스텝 3지선다 `</silence>` / `</response>` / `</delegate>` | `live_adapter.py:100` `DEFAULT_SYSTEM_PROMPT_EN`; `live_adapter.py:normalize_model_output()` / `extract_response_payload()` | 시스템 프롬프트가 3개 결정 토큰 포맷 강제. 모델 자유출력을 어댑터가 첫 마커 기준으로 표준형(`</silence>` \| `</response> <payload>`)으로 정규화. 위임은 코드상 `</delegation>`. | P1·P2 |
| 4 | 1 Hz per-second 관찰 (1타임스텝=1초) | `video_processor.py:59` `process_interval_seconds=1.0`; `live_adapter.py:498` `frame_seconds=1.0`, `_parse_start_second()`; README `FRAME_SECONDS=1.0` | 초당 1프레임 샘플링·전송. 프레임 인덱스×`frame_seconds`로 "N.0 seconds" 시간범위 라벨 부여(`_time_range_for_frame`). | P0·P1·P2 |
| 5 | 실시간 루프(WebRTC/RTSP 수신, 지연 드롭) | `webui/…/server.py:offer()` + `video_processor.py:VideoProcessorTrack.recv()` | aiortc `RTCPeerConnection`으로 웹캠/RTSP 트랙 구독. 1Hz 게이트 + `max_frame_latency` 초과 프레임 드롭(최대 100프레임 안전루프), zero-copy passthrough. | P2 |
| 6 | AdaCodec: 프레임당 토큰 비용을 장면 복잡도에 비례해 절감 | **미구현(로드맵)**. 대체: `live_adapter.py:499` `max_pixels=262144` + `_resize_image_if_needed`, `chunk=200`/`compress_every_n_chunks=5`, `start_model.sh:48` `--enable-prefix-caching` | 코덱 대신 공간 다운스케일·메모리 압축·prefix 캐시로 장시간 토큰 예산 관리. P-토큰/참조프레임 개념은 코드에 없음. | P0 |
| 7 | 예측 프레임 압축 인코딩(모션/잔차) | **미구현**. 매 프레임 독립 JPEG 풀 인코딩: `vlm_service.py:158` `image.save(..., format="JPEG")` → base64 | 프레임 간 예측·잔차 부호화 없음. 각 프레임을 온전한 이미지로 base64 data URL화하여 전송. | P0 |
| 8 | 결정 토큰을 출력에 보존 | `live_adapter.py:1599` `_main_generation_kwargs` → `extra_body.setdefault("skip_special_tokens", False)` | `</silence>` 등 특수 토큰이 디코딩에서 잘리지 않도록 강제. | P0 |
| 9 | "질의 없으면 능동적 침묵" | `live_adapter.py: FORCE_SILENCE_BEFORE_QUERY=True` | 사용자 질문 부재 시 main model을 호출하지 않고 어댑터가 곧바로 `</silence>` 반환(저비용 단락 경로). | P1 |
| 10 | 계층적 장기 기억(단기 원본 / 중기 요약 / 장기 블록; T_s=100/M=5/L=15) | `live_adapter.py:_flush_chunk()/_compress_mid_terms()` (`CHUNK`, `COMPRESS_EVERY_N_CHUNKS=5`) + `memory_summarizer.py:generate_detailed_summary()/batch_compress_to_longterm()` | 청크 경계에서 중기 멀티모달 요약, N청크마다 장기 텍스트 압축. `build_static/dynamic_system_content()`로 시스템 프롬프트에 주입→prefix caching 대상. 논문 상수는 `chunk`/`compress_every_n_chunks`/버퍼 상한으로 파라미터화(정확 기본값 확인 필요 — **추정**). | P1·P2 |
| 11 | 백그라운드 작업 위임(`</delegate>`) | `webui/…/background_model.py:parse_delegation()/handle_foreground_response()/_run_delegation_task()` | 포그라운드 응답의 `<delegation>`/`<\|background_call\|>` 감지 → 프레임 스냅샷과 함께 위임 태스크 비동기 실행, 사용자엔 즉시 플레이스홀더 반환. | P2 |
| 12 | 비동기 루프 + 모델-무관 텍스트 프로토콜 + 타임아웃/취소/병합 | `background-agent/codex_api/main.py:POST /v1/solve`, `_solve_with_codex()` | codex CLI 서브프로세스로 무거운 하위작업 처리, JSONL 이벤트 파싱, `asyncio.wait_for(timeout_seconds=600)`. `status ∈ {completed,failed,timeout}`. | P2 |
| 13 | 결과 병합(merge) → 사용자 전달 | `background_model.py`(완료 콜백) → `server.py:get_session_callback()/send_to_session()` | 완료 결과·리치 콘텐츠(HTML/차트)를 세션 WebSocket으로 통지, 메인 대화에 병합. | P2 |
| 14 | 4M+ 시간정렬 데이터, 침묵을 일급 라벨로 | `datasets/convert_data.py:convert_sample()` 루프 | 초마다 user(질문+시간마커+`<image>`) / assistant(`</response> …` 또는 `</silence>`) 쌍 생성 — 침묵이 정식 정답 문자열로 존재. | P1 |
| 15 | 가중 CE 손실 (응답 1.5 / 첫 침묵 1 / 반복 침묵 0.4) | **(저장소 밖)** LLaMA-Factory 학습 설정 — `convert_data.py`는 라벨만 생성, **가중치 미적용** | 데이터는 평문 `</silence>`/`</response>`만 방출. 가중치는 학습 프레임워크 단계에서 부여(§6 참조). | P1 |
| 16 | RL 후처리 (GRPO, answer-centered window) | **(저장소 밖)** EasyVideoR1 프레임워크 | README가 RL 프레임워크로 EasyVideoR1 명시. | P1 |
| 17 | 플러그형 ASR/TTS | `server.py:setup_asr_routes()/setup_tts_routes()`, `asr.py`/`tts.py` | 실시간 루프와 독립적으로 동작하는 음성 입출력 라우트. | P2 |
| 18 | 시각화 UI | `webui/…/static/index.html`, `server.py:index()` | 브라우저 프런트엔드에서 스트림·응답·메트릭 표시. | P2 |

> 매핑 주의: 논문 태그 `</delegate>`는 코드상 `</delegation>`/`<|background_call|>`, 요약 하이퍼파라미터 T_s=100/M=5/L=15는 코드에서 `chunk`·`compress_every_n_chunks`·`key_frames_per_chunk` 등 설정값으로 일반화되어 있음(정확한 기본값은 어댑터 설정 로드 시점 확인 필요 — **추정**).

---
---

# Part 0 — AdaCodec 시각 코덱 + 비전-언어 베이스 모델

> 이 부분은 전체 구현 중 **"AdaCodec 시각 코덱 + 비전-언어 베이스 모델"** 하위시스템만 분석합니다.
> 즉 "영상 프레임이 어떻게 토큰으로 인코딩되어 베이스 VLM에 들어가고, 그 베이스 모델이 어떻게 서빙되는가"에 집중합니다.

## 1. 디렉토리 구조 (이 모듈 관련만)

```
services/
├── webinfer/                         # 베이스 VLM 서빙 + 프레임→토큰 어댑터 (이 모듈의 심장)
│   ├── live_adapter.py               # 프레임/시간범위→OpenAI 메시지 조립, vLLM 호출
│   ├── scripts/start_model.sh        # 메인 VLM(JoyAI-VL-Interaction-Preview) vLLM 기동
│   ├── scripts/start_all_models.sh   # 메인+요약 모델 동시 기동
│   └── scripts/start_summary_model.sh# 요약용 Qwen3-VL-4B (포트 8065)
├── webui/src/joy_interaction_webui/
│   ├── video_processor.py            # WebRTC 프레임 1Hz 샘플링 → PIL
│   └── vlm_service.py                # PIL → JPEG → base64 data URL → VLM API 호출(클라이언트)
install/download-models.sh            # JoyAI-VL-Interaction-Preview / Qwen3-VL-4B 다운로드
doc/architecture.md                   # 서비스 토폴로지·포트·GPU 배치
```

## 2. 데이터 흐름 추적 (입력 → 인코딩 → 베이스 모델 → 출력)

```
[WebRTC/RTSP 영상 스트림]
     │  aiortc VideoStreamTrack
     ▼
video_processor.py: VideoProcessorTrack.recv()
     │  process_interval_seconds=1.0 → 1초에 1프레임만 통과
     │  frame.to_ndarray("bgr24") → PIL.Image (BGR→RGB)
     ▼
vlm_service.py: VLMService.analyze_image()
     │  PIL → JPEG(BytesIO) → base64 → "data:image/jpeg;base64,…"  ← 프레임 "인코딩"의 실체
     │  content=[{type:text, …},{type:image_url, image_url:{url:…}}]
     ▼  (OpenAI-호환 HTTP, x-streaming-session 헤더로 세션 고정)
webinfer/live_adapter.py: StreamingInferAdapter._handle_chat_payload()
     │  _extract_all_image_refs → _resolve_frame_ref (data_url 또는 로컬 경로)
     │  _time_range_for_frame(i) = "i.0 seconds"  → <시간 마커> 텍스트 삽입
     │  프레임을 current_chunk에 누적(chunk=200), 메모리 프리픽스 조립
     │  _build_main_internal_messages → _build_cached_api_messages(prefix caching 재사용)
     │  _resize_image_if_needed(max_pixels=262144)로 공간 토큰 상한
     ▼
_call_main_model() → vLLM(JoyAI-VL-Interaction-Preview, 포트 7060)
     │  ViT가 각 프레임을 시각 토큰으로 변환(모델 내부) + LM이 다음 토큰 생성
     │  _main_generation_kwargs: skip_special_tokens=False, temperature=0.8, top_p=0.9, top_k=40, max_tokens=128
     ▼
raw_text = "</silence>" | "</response> …" | "</response> … </delegation> <question>"
     │  normalize_model_output / extract_response_payload 로 후처리
     ▼  결정 토큰 파싱 → UI/TTS/백그라운드로 라우팅(다른 module)
```

핵심: **"AdaCodec 시각 인코딩"의 실제 코드 경로는 `frame.to_ndarray` → `PIL` → `JPEG` → `base64 data URL`이며, 시각 토큰화(ViT)는 vLLM에 로드된 모델 가중치 내부에서 일어납니다.** 저장소 코드는 프레임을 "이미지 URL"로 넘길 뿐, 코덱/토큰 압축 로직을 직접 구현하지 않습니다.

## 3. 핵심 코드 발췌 + 해설

**(a) 베이스 VLM 기동 — `services/webinfer/scripts/start_model.sh`**
```bash
MODEL_PATH="${MODEL_PATH:-/tmp/models/JoyAI-VL-Interaction-Preview}"
SERVED_MODEL_NAME="${SERVED_MODEL_NAME:-JoyAI-VL-Interaction-Preview}"
MAX_MODEL_LEN="${MAX_MODEL_LEN:-131072}"                      # 장시간 스트림용 128K 컨텍스트
CUDA_VISIBLE_DEVICES="${MAIN_GPU}" python -m vllm.entrypoints.openai.api_server \
    --model "${MODEL_PATH}" --served-model-name "${SERVED_MODEL_NAME}" \
    --port 7060 --gpu-memory-utilization 0.9 \
    --max-model-len "${MAX_MODEL_LEN}" \
    --enable-prefix-caching --enable-chunked-prefill          # 논문의 prefix caching 주장에 대응
```
→ 베이스 모델은 **커스텀 파이썬 클래스가 아니라 표준 vLLM에 올린 가중치**입니다. 논문의 "Qwen3-8B 초기화 LM + Qwen3-VL ViT"는 이 가중치(`JoyAI-VL-Interaction-Preview`)에 내재하며, 저장소에는 모델 클래스 정의가 없습니다(→ 재현성 참고).

**(b) 프레임 "인코딩" 실체 — `services/webui/src/joy_interaction_webui/vlm_service.py:analyze_image`**
```python
img_byte_arr = io.BytesIO()
image.save(img_byte_arr, format="JPEG")                       # 예측·잔차 없이 매 프레임 풀 JPEG
img_base64 = base64.b64encode(img_byte_arr.getvalue()).decode("utf-8")
image_url = f"data:image/jpeg;base64,{img_base64}"
content.append({"type": "image_url", "image_url": {"url": image_url}})
```
→ AdaCodec의 "P-토큰"이 없으므로 **모든 프레임이 참조 프레임처럼 온전히 인코딩**됩니다. 토큰 절감은 코덱이 아니라 아래 (c)(d)로 처리합니다.

**(c) 공간 토큰 상한 — `services/webinfer/live_adapter.py`**
```python
max_pixels: int = 262144            # ≈512×512, 프레임당 시각 토큰 수 상한(AdaCodec 대체 장치 1)
...
def _resize_image_if_needed(image, max_pixels):
    if max_pixels <= 0 or width*height <= max_pixels: return None
    scale = (max_pixels/(width*height))**0.5
    return image.resize((int(width*scale), int(height*scale)), Image.LANCZOS)
```
→ 해상도를 낮춰 프레임당 ViT 토큰 수를 상한. `chunk=200`(200프레임마다 청크 마감)·`compress_every_n_chunks=5`(계층 요약)와 결합해 장시간 토큰 예산을 억제합니다(AdaCodec의 "장면 복잡도 비례 비용" 목표를 시스템으로 근사).

**(d) 결정 토큰 보존 + 샘플링 — `_main_generation_kwargs`**
```python
extra_body.setdefault("skip_special_tokens", False)          # </silence></response></delegation> 유지
...
"max_tokens": self.config.main_max_tokens,   # 128 (짧은 결정/응답)
"temperature": 0.8, "top_p": 0.9, "top_k": 40, "repetition_penalty": 1.0
```
→ 베이스 모델 출력이 3지선다 결정 토큰을 담으므로 특수 토큰을 절대 스킵하지 않게 강제. 이것이 이 module과 "per-second 결정" module을 잇는 접점입니다.

**(e) 결정 포맷 계약 — `DEFAULT_SYSTEM_PROMPT_EN` (live_adapter.py:100)**
```
At every inference step you MUST choose exactly one of the following three actions:
</silence>                              # 침묵
</response> Your reply here.            # 응답
</response> Brief note… </delegation> <the question>   # 위임
```
→ 논문의 `</silence>/</response>/</delegate>`가 코드에서는 `</silence>/</response>/</delegation>`으로 실현됨(위임은 `</response>` 뒤에 `</delegation>` 태그를 붙이는 2단 포맷).

## 4. 의존성 / 환경 요구사항

- **GPU**: 베이스 VLM은 GPU 0에 `gpu-memory-utilization=0.9`, `max-model-len=131072`. 8B + 128K 컨텍스트라 대용량 VRAM(단일 고사양 GPU 또는 텐서병렬 `TENSOR_PARALLEL_SIZE`) 필요.
- **런타임**: Python 3.12, `vllm`(OpenAI api_server), CUDA 12.x. 어댑터: `aiohttp`, `openai`, `Pillow`. WebUI 프레임: `aiortc`, `av`, `numpy`, (오버레이 시) `opencv`.
- **모델 가중치**: `install/download-models.sh --all` → 메인 `JoyAI-VL-Interaction-Preview`(HF `jdopensource/…`), 요약 `Qwen/Qwen3-VL-4B-Instruct`(포트 8065). 이 module 최소 구동에는 **메인 모델만** 있으면 됨.
- **포트**: 어댑터 8070, 메인 VLM 7060(internal). 어댑터 config는 `main_api_base="http://127.0.0.1:7060/v1"`.

## 5. 최소 재현(Minimal Repro) 가능 여부와 경로

- **부분 가능**. 이 module의 *서빙·인코딩 파이프라인*은 재현 가능:
  1. `./install/download-models.sh --all`로 `JoyAI-VL-Interaction-Preview` 확보
  2. `MODEL_PATH=… bash services/webinfer/scripts/start_model.sh` (포트 7060 vLLM)
  3. `python services/webinfer/live_adapter.py`(포트 8070 어댑터)로 기동 후, `frame + <time_range>`를 담은 OpenAI-호환 `chat/completions` 요청을 보내면 프레임→토큰→결정 흐름을 검증 가능.
  4. WebUI 없이도 `vlm_service.py`의 JPEG/base64 로직만 떼어 단위 확인 가능.
- **불가능한 부분**: **AdaCodec 코덱 자체는 재현 불가**(코드 미공개, 로드맵). 또한 ViT 토큰화·모델 아키텍처 정의가 저장소에 없어 "P-토큰당 16토큰" 같은 논문 수치는 코드로 검증 불가.
- `/code-run` 사용 시 주의: 실제 GPU·8B 가중치가 없으면 3번은 실행 불가하며, **더미 vLLM 목(mock)**으로 `live_adapter.py`의 메시지 조립·`_resize_image_if_needed`·`skip_special_tokens` 경로만 스모크 테스트하는 것을 권장.

---
---

# Part 1 — 실시간 per-second 의사결정 루프 + 학습 파이프라인

> 본 부분은 **실시간 per-second 의사결정 루프(webinfer) + 학습 파이프라인의 데이터 변환/프레임워크 축**만 분석합니다.

- **실행 진입점(이 하위시스템)**:
  - 실시간 의사결정: `services/webinfer/live_adapter.py` (OpenAI 호환 `/v1/chat/completions`), 기동 `services/webinfer/scripts/start_adapter.sh`
  - 학습 데이터 파이프라인: `datasets/convert_data.py` (`convert_sample(...)`)

## 1. 디렉토리 구조 (이 하위시스템 관련 핵심만)

```
services/
└── webinfer/                      # ← 실시간 per-second 의사결정 서비스 (본 하위시스템의 심장)
    ├── live_adapter.py            #   프레임 1Hz 수신 → 결정 토큰 정규화/라우팅 → main model 호출
    ├── memory_summarizer.py       #   chunk 요약/장기 압축 (결정 루프에 컨텍스트 공급 — 접점만)
    ├── README.md / README.zh-CN.md#   환경변수·API·결정 출력 포맷 명세
    └── scripts/
        ├── run.sh                 #   webinfer 일괄 기동
        ├── start_adapter.sh       #   실시간 어댑터 단독 기동
        ├── start_model.sh         #   main model(vLLM) 서빙
        ├── start_all_models.sh    #   main + summary 동시 기동
        ├── start_summary_model.sh #   요약 모델(Qwen3-VL-4B) 서빙
        └── stop.sh
datasets/                          # ← 학습 파이프라인의 데이터 변환 축
    ├── convert_data.py            #   원시 시간정렬 주석 → per-second user/assistant 메시지 쌍
    └── README.md                  #   HF `jdopensource/JoyAI-VL-Interaction` 다운로드·구성법
services/scripts/run.sh            # 전체 스택 오케스트레이션 (`./services/scripts/run.sh minimal`)
```

> 주: 저장소에는 **가중 CE 손실을 직접 구현한 학습 코드 파일이 포함되어 있지 않습니다**. SFT는 LLaMA-Factory, RL은 EasyVideoR1이라는 **외부 프레임워크에 위임**되며, 저장소가 제공하는 학습 측 산출물은 (a) `datasets/convert_data.py`의 per-second 포맷 변환과 (b) HuggingFace `jdopensource/JoyAI-VL-Interaction` 데이터셋입니다.

## 2. 데이터 흐름 추적

### (A) 추론 시: 실시간 per-second 결정 루프
```
WebRTC/RTSP JPEG (~1 fps)
        │
        ▼  POST /v1/chat/completions  (헤더 x-streaming-session:<id>)
live_adapter.py: _handle_chat_payload()
        │ 1) 세션/모델/메시지/이미지/타임스탬프 추출
        │ 2) FORCE_SILENCE_BEFORE_QUERY & 질의 없음  ──► 즉시 "</silence>" 반환 (main model 미호출)
        │ 3) _build_internal_user_message(): 질의 헤더 + <start~end> 시간마커 + <image>(max_pixels=262144)
        │ 4) _build_main_internal_messages(): static(장/중기 기억+영상 이력)+dynamic(현재 질의) system content 결합
        │ 5) _call_main_model(api_messages) → client.chat.completions.create(...)
        │ 6) normalize_model_output() → "</silence>" | "</response> <payload>"
        │    extract_response_payload() → 발화 텍스트(있으면)
        │ 7) turn_count ≥ CHUNK 이면 _flush_chunk() (중기 요약, 비동기 가능)
        │    중기 요약 ≥ COMPRESS_EVERY_N_CHUNKS 이면 _compress_mid_terms() → 장기 기억 갱신
        ▼
Chat Completion JSON  (+ streamingharness.memory / .timing / .raw_content)
        └► </response>면 payload를 TTS로, </silence>면 무발화
```

### (B) 학습 시: 시간정렬 주석 → per-second 지도 데이터
```
원시 주석 (video + fps + question_map{sec→질문} + response_map{sec→정답발화})
        │
        ▼  datasets/convert_data.py: convert_sample(sample, frame_dir, ...)
초 s = 0 … n_seconds-1 마다:
   user   := (질문 있으면 질문) + "<{s}.0 seconds>" + "<image>" × frames_per_sec
   assistant := response_map[s] 있으면 "</response> {발화}"  else "</silence>"
        │
        ▼
{ messages:[…], images:[frame_paths], video_name, video_path, task_type, source }  (JSON 배열)
        └► LLaMA-Factory(SFT, 가중 CE) → EasyVideoR1(GRPO) 로 학습  (외부 프레임워크)
```

## 3. 핵심 코드 발췌 + 해설

### 3-A. per-second 결정 토큰 정규화 (`live_adapter.py`)
main model의 자유형 출력을 **정확히 세 결정 중 하나의 표준형**으로 환원하는 지점이 실시간 의사결정의 핵심입니다.

```python
def normalize_model_output(text: str) -> str:
    # 가장 앞선 마커(</response> 또는 </silence>)를 탐색해 표준형으로 환원
    # 반환: "</silence>"  |  "</response> [payload]"  | (마커 없으면) 첫 줄 추정

def extract_response_payload(text: str) -> Optional[str]:
    # </silence>로 시작하면 None (발화 없음)
    # 그 외에는 </response> 뒤의 트리밍된 발화 텍스트 반환
```
해설: 논문의 "매초 셋 중 하나를 반드시 고른다"가 코드에서는 **출력 후처리 정규화**로 구현됩니다. `</silence>`는 발화 없음(payload=None), `</response>`는 한 문장 발화. `</delegate>`(코드 표기 `</delegation> <the question>`)는 시스템 프롬프트 템플릿에 정의되어 있으나 **현 어댑터가 능동적으로 파싱/라우팅하진 않는** 상태 — 위임은 프로토콜/프롬프트 레벨에서 존재하되 실시간 어댑터의 라우팅은 speak/silence 중심(추정: 배경 브릿지는 `services/background-agent`가 담당).

### 3-B. "질의 전 강제 침묵" 단락 경로 (`live_adapter.py` / README)
```
FORCE_SILENCE_BEFORE_QUERY = True (default)
→ "without a user question, the adapter returns </silence> directly and does not call the main model."
```
해설: per-second 루프의 **대부분이 침묵**이라는 통계적 사실을 추론 비용 관점에서 활용. 질의가 없는 초는 main model을 아예 호출하지 않고 어댑터가 `</silence>`로 즉답 → 서브초 지연·GPU 절약. 학습에서 반복 침묵 가중치를 0.4로 낮춘 것의 **추론판 대응**.

### 3-C. per-second 지도 데이터 생성 (`datasets/convert_data.py`)
```python
frames_per_sec = max(int(fps), 1)
n_seconds = max(int(effective_duration), 1)

messages = []
for sec in range(n_seconds):
    parts = []
    if sec in question_map:
        parts.append(question_map[sec])
    parts.append(f"<{sec:.1f} seconds>")
    for _ in range(frames_per_sec):
        parts.append("<image>")
    messages.append({"role": "user", "content": "\n".join(parts)})

    if sec in response_map:
        messages.append({"role": "assistant",
                         "content": f"</response> {response_map[sec]}"})
    else:
        messages.append({"role": "assistant", "content": "</silence>"})
```
해설: 논문의 **"침묵을 라벨 부재가 아닌 일급 라벨로"**가 정확히 여기서 실현됩니다 — 응답이 없는 초는 빈칸이 아니라 `{"role":"assistant","content":"</silence>"}`라는 정식 assistant 턴으로 채워집니다. 시간마커 `"<{sec}.0 seconds>"`가 **타이밍 감독**(언제 말할지)을, `</response> {text}`가 **내용 감독**(무엇을 말할지)을 담당해 "2축 검증"과 대응.
**중요(정직한 한계 표기)**: 이 스크립트는 `</silence>`를 **평문 그대로** 방출할 뿐, `w_silence_first=1 / w_silence_repeated=0.4 / w_response=1.5`의 **가중치를 적용하지 않습니다**. 즉 **가중 CE는 데이터 포맷이 아니라 학습 단계(LLaMA-Factory)에서 토큰/샘플 가중으로 부여**됩니다.

### 3-D. 결정 루프에 기억을 주입 (접점만)
```python
def build_static_system_content(extra_system_messages=None, memory_state=None,
                                mid_term_summaries=None, language="en") -> str
def build_dynamic_system_content(current_query_text=None, memory_state=None,
                                include_qa_history=True, current_chunk_index=0, language="en") -> str
# CHUNK=200(코드 default) / 100(스크립트 default), COMPRESS_EVERY_N_CHUNKS=5
```
해설: static(장·중기 기억+영상 이력) + dynamic(현재 질의)로 시스템 프롬프트를 구성해 매 결정에 컨텍스트를 공급. `prefix caching`으로 과거 인코딩을 재사용. 기억의 3층 세부(T_s/M/L)는 `memory_summarizer.py` 담당.

## 4. 의존성 / 환경 요구사항

- **런타임**: Python 3.12+, CUDA 12.x, GPU(8B main + 4B summary 서빙)
- **서빙**: vLLM (OpenAI 호환). main model `jdopensource/JoyAI-VL-Interaction-Preview`, 요약 `Qwen/Qwen3-VL-4B-Instruct`
- **설치/다운로드**: `install/install.sh`, `install/download-models.sh`
- **학습(외부)**: LLaMA-Factory(SFT), EasyVideoR1(RL/GRPO) — 저장소에 미포함, 별도 설치 필요
- **핵심 환경변수(webinfer README 기준, 기본값)**:

  | 변수 | 기본값 | 의미 |
  |---|---|---|
  | `ADAPTER_PORT` | 8070 | 어댑터 HTTP 포트 |
  | `FRAME_SECONDS` | 1.0 | 1 Hz 샘플링(타임스텝=1초) |
  | `FORCE_SILENCE_BEFORE_QUERY` | true | 질의 전 강제 침묵 단락 |
  | `CHUNK` | 100(스크립트)/200(코드) | 요약 트리거 프레임 수 |
  | `COMPRESS_EVERY_N_CHUNKS` | 5 | 장기 압축 트리거(=논문 M=5) |
  | `ASYNC_SUMMARY_LEAD_FRAMES` | 20(스크립트)/10(코드) | 비동기 요약 선행 프레임 |
  | `MAIN_MAX_TOKENS` | 256 | 한 문장 발화 상한 |
  | `MAIN_TEMPERATURE` | 0.8 | main model 샘플링 온도 |
  | `SUMMARIZER_MAX_PIXELS` / `max_pixels` | 262144 | 프레임 해상도 상한 |
  | `MODEL_PATH` | /tmp/models/JoyAI-VL-Interaction-Preview | main 모델 경로 |
  | `SUMMARY_MODEL_PATH` | /tmp/models/Qwen3-VL-4B-Instruct | 요약 모델 경로 |

- **API**: `GET /health`, `GET /v1/models`, `POST /v1/chat/completions`, `POST /v1/streaming/reset`. 세션은 헤더 `x-streaming-session:<id>` 또는 body `user`.

## 5. 최소 재현(Minimal Repro) 가능 여부와 경로

- **추론(실시간 per-second 결정) — 재현 가능(하드웨어 있으면)**:
  1. `install/install.sh` → `install/download-models.sh`
  2. main+요약 모델 서빙: `services/webinfer/scripts/start_all_models.sh`
  3. 어댑터 기동: `services/webinfer/scripts/start_adapter.sh` (또는 `services/scripts/run.sh minimal`)
  4. `POST /v1/chat/completions`에 시간마커+`<image>` 프레임 시퀀스 전송 → 응답이 `</silence>`/`</response> …`로 오는지 확인. 질의 없이 보내면 `FORCE_SILENCE_BEFORE_QUERY`로 즉시 `</silence>` 확인.
- **학습 데이터 파이프라인 — 부분 재현 가능**: `datasets/convert_data.py`로 HF 주석을 per-second 메시지 쌍(JSON)으로 변환 가능. 침묵 일급 라벨·시간마커까지 정확히 재현.
- **가중 CE 손실 / GRPO — 저장소 코드만으로는 재현 불가(외부 프레임워크 필요)**: `w_response=1.5 / w_silence_first=1 / w_silence_repeated=0.4`는 데이터가 아니라 **LLaMA-Factory 학습 설정**에서 부여해야 함. RL은 EasyVideoR1로 별도 구성.

### 정직성 메모 (추측 표기)
- `</delegate>` 실시간 라우팅: 코드는 프롬프트 템플릿에만 정의, 어댑터 능동 파싱 미확인 → 위임 실행은 `services/background-agent`로 **추정**.
- `CHUNK`/`ASYNC_SUMMARY_LEAD_FRAMES` 기본값이 코드 dataclass와 기동 스크립트에서 상이(200 vs 100, 10 vs 20) — 스크립트 값이 배포 기본으로 **추정**.
- 학습 측 가중치 적용부는 저장소 외부(LLaMA-Factory)라 파일 경로 **미확인**.

---
---

# Part 2 — 배포 가능한 종단 서빙 스택

> **분석 범위(모듈)**: `serving_stack_vllm_websocket_memory_delegation_ui`
> 즉 **배포 가능한 종단 시스템(end-to-end serving stack)**만 다룬다:
> ① vLLM 네이티브 서빙, ② WebRTC/WebSocket 실시간 루프, ③ 계층적 기억(요약) 파이프라인, ④ 백그라운드 위임(delegate), ⑤ 시각화 UI.

- **실행 진입점(서빙 스택 전체)**: `services/scripts/run.sh` (`minimal` | `all` 모드) → 각 서비스 `scripts/*.sh` → WebUI를 포그라운드로 기동, `https://127.0.0.1:8099` 접속.

## 1. 디렉토리 구조 (서빙 스택 핵심만)

```
services/
├── scripts/
│   ├── run.sh                 # 전체 오케스트레이션(minimal/all), 헬스체크 후 WebUI 기동
│   └── stop.sh
├── webinfer/                  # ── vLLM 서빙 + 실시간 추론 어댑터 + 계층적 기억 ──
│   ├── live_adapter.py        # OpenAI-호환 추론 어댑터: 세션/청크/프리픽스 캐싱/요약 트리거
│   ├── memory_summarizer.py   # 중기(멀티모달)·장기(텍스트) 요약 압축
│   └── scripts/
│       ├── start_model.sh         # 메인 VLM을 vLLM으로 서빙 (:7060)
│       ├── start_summary_model.sh # 요약 전용 VLM을 vLLM으로 서빙 (:8065)
│       ├── start_adapter.sh       # live_adapter 기동 (:8070)
│       └── start_all_models.sh / run.sh / stop.sh
├── webui/                     # ── WebRTC/WebSocket 실시간 루프 + 시각화 UI ──
│   └── src/joy_interaction_webui/
│       ├── server.py              # aiohttp 앱: /offer(WebRTC), /ws(WebSocket), 세션 관리
│       ├── video_processor.py     # VideoProcessorTrack: 1Hz 샘플링·배칭·지연드롭
│       ├── vlm_service.py         # webinfer 어댑터 호출(프레임→base64→OpenAI 메시지)
│       ├── background_model.py    # </delegation> 감지→백그라운드 위임·병합
│       ├── rtsp_track.py          # RTSP(IP 카메라) 소스 트랙
│       ├── asr.py / tts.py        # ASR/TTS 라우트 연동(플러그형)
│       └── static/index.html      # 브라우저 프런트엔드(시각화 UI)
├── background-agent/          # ── 비동기 위임 백엔드(모델-무관 프로토콜) ──
│   └── codex_api/main.py          # FastAPI: POST /v1/solve, codex 서브프로세스 구동
├── asr/  tts/                 # 플러그형 음성 어댑터(범위상 보조)
```

**포트 맵(서빙 스택)** — `run.sh` 헬스체크 기준:

| 컴포넌트 | 포트 | 헬스 URL |
|---|---|---|
| Webinfer 메인 VLM (vLLM) | 7060 | `/v1/models` |
| Webinfer 요약 VLM (vLLM) | 8065 | `/v1/models` |
| Webinfer 어댑터(live_adapter) | 8070 | `/health` |
| WebUI (aiohttp, WebRTC/WS) | 8099 | `https://127.0.0.1:8099` |
| Background-agent (FastAPI) | 8079 | `/health` |
| ASR vLLM / 어댑터 | 8993 / 8994 | — |
| TTS vLLM-Omni / 어댑터 | 8991 / 8992 | — |

## 2. 데이터 흐름 추적 (입력 → … → 출력)

```
[브라우저 웹캠 / RTSP IP카메라]
        │  WebRTC(SDP offer) 또는 RTSP URL
        ▼
server.py:offer()  ── RTCPeerConnection 생성, session = get_or_create_session(id)
        │  pc.on("track") / RTSPVideoTrack → relay.subscribe()
        ▼
VideoProcessorTrack.recv()  ── 매 프레임 수신
        │  ├─ max_frame_latency 초과 프레임 드롭(최대 100프레임 안전루프)
        │  ├─ 1Hz 게이트: time_since_last >= process_interval_seconds(=1.0)
        │  └─ frames_per_batch>1이면 버퍼링 후 process_frame_batch()
        ▼
vlm_service.py:analyze_image()/analyze_images()
        │  PIL→JPEG→base64 "data:image/jpeg;base64,…" 를 OpenAI 메시지로 구성
        │  헤더 x-streaming-session=session_id, 옵션 frame_time_range
        │  POST → webinfer 어댑터(:8070) → (내부) vLLM 메인 VLM(:7060)
        ▼
live_adapter.py:handle_chat_completions() → _handle_chat_payload()
        │  ├─ 프리픽스(정적 시스템+동적 쿼리/이력) 메시지 조립(_build_cached_api_messages)
        │  ├─ 청크 경계 도달 시 _flush_chunk() → 중기 요약(summary VLM :8065)
        │  ├─ mid_term 누적 ≥ N → _compress_mid_terms() → 장기 블록 압축
        │  ├─ (쿼리 없고 force_silence_before_query면) 강제 침묵
        │  └─ 메인 VLM 호출 → normalize_model_output()
        ▼
   ┌────────────── 결정 분기 (per-second) ──────────────┐
   │ </silence>  → 응답 텍스트 없음(계속 관찰)          │
   │ </response> → extract_response_payload() 텍스트     │
   │ </delegation>/<|background_call|> → 위임 트리거     │
   └────────────────────────────────────────────────────┘
        ▼
VideoProcessorTrack.recv(): vlm_service.get_current_response()+get_metrics()
        │  → text_callback(response, metrics)
        ▼
server.py:get_session_callback():
        │  background_service.handle_foreground_response(text) 로 위임 감지·치환
        │  {"type":"vlm_response","text":…,"metrics":…} → send_to_session() (WebSocket)
        ▼
[static/index.html UI]  ── 응답/메트릭 표시  ── (옵션) TTS 라우트로 음성 합성

── 병렬(비동기 루프) ──────────────────────────────────
background_model.py:_run_delegation_task():
        POST /v1/solve → background-agent(:8079)
        codex_api/main.py:_solve_with_codex():
          프레임 임시저장 → codex 서브프로세스(CODEX_HOME) → stdout JSONL 파싱
          asyncio.wait_for(timeout_seconds=600) 타임아웃/취소
        완료 → SolveResponse(status/text/usage/…) → 리치콘텐츠·요약 추출
        → notify_session_json() → send_to_session()  (메인 대화로 merge)
```

## 3. 핵심 코드 발췌 + 해설

### 3.1 vLLM 네이티브 서빙 — `webinfer/scripts/start_model.sh`

```bash
CUDA_VISIBLE_DEVICES="${MAIN_GPU}" "${PYTHON_BIN}" -m vllm.entrypoints.openai.api_server \
    --model "${MODEL_PATH}" \
    --served-model-name "${SERVED_MODEL_NAME}" \        # 기본 JoyAI-VL-Interaction-Preview
    --port "${MAIN_MODEL_PORT}" \                        # 기본 7060
    --gpu-memory-utilization "${MAIN_GPU_MEMORY_UTILIZATION}" \  # 0.9
    --max-model-len "${MAX_MODEL_LEN}" \                 # 131072
    --tensor-parallel-size "${TENSOR_PARALLEL_SIZE}" \
    --data-parallel-size "${DATA_PARALLEL_SIZE}" \
    --data-parallel-size-local "${DATA_PARALLEL_SIZE_LOCAL}" \
    --enable-prefix-caching \                            # ★ 논문의 서브초 지연 핵심
    --enable-chunked-prefill
```

- **해설**: 논문이 강조한 "표준 vLLM 인프라 + prefix caching"이 그대로 플래그로 존재. `--enable-prefix-caching`은 매초 재계산 없이 과거 컨텍스트 KV를 재사용해 2시간+ 스트림에서도 종단 지연을 억제한다. `max-model-len=131072`는 계층적 기억으로 압축된 ~2시간 맥락을 담기 위한 컨텍스트 예산.
- 요약 모델은 `start_summary_model.sh`로 별도 vLLM 인스턴스(:8065, 기본 `Qwen3-VL-4B-Instruct`, `--max-model-len 65536`)로 분리 서빙 — 실시간 추론 GPU와 요약 부하를 격리.

### 3.2 per-second 결정 파싱 — `webinfer/live_adapter.py`

`normalize_model_output(text)`의 동작(요지):
- `</response>`, `</silence>`, `</delegation>` 중 **첫 마커 위치**를 찾고,
- 마커가 없거나 내용이 비면 **`"</silence>"`** 반환(=능동적 침묵),
- 응답 마커면 `"</response> {first_line}"` 형태로 정규화, `extract_response_payload()`가 `</response>` 접두를 떼어 순수 응답 텍스트를 추출.

- **해설**: 논문의 3지선다가 **어댑터 계층의 문자열 정규화**로 구현된다. `AdapterConfig`에는 `main_api_base=http://127.0.0.1:7060/v1`, `main_model=…-8b`, `summarizer_api_base=http://127.0.0.1:8065/v1`가 담겨 vLLM 두 인스턴스를 연결한다.

### 3.3 계층적 기억 — `live_adapter.py` + `memory_summarizer.py`

- **청크(중기 요약) 트리거**: `_handle_chat_payload()`가 `turn_count >= config.chunk`이면 `_flush_chunk()` → `_build_mid_term_summary_entry()` → `summarizer.generate_detailed_summary()`. 결과는 `state.mid_term_summaries`에 버퍼.
- **장기 압축**: `len(mid_term_summaries) >= compress_every_n_chunks`이면 `_compress_mid_terms()` → `summarizer.batch_compress_to_longterm()` → `state.memory_state["long_term_memory"]` 갱신.
- `memory_summarizer.py`:
  - `generate_detailed_summary()`: 청크당 최대 `key_frames_per_chunk`(기본 8) 프레임을 `_sample_sorted_indices()`로 뽑아 base64 멀티모달 프롬프트로 요약 VLM 호출. 프롬프트는 **비가역 상태 변화·중간 결과·읽을 수 있는 텍스트/수치·최초 등장 이벤트·공간 관계**를 우선 보존(`mid_term_max_tokens=5000`).
  - `batch_compress_to_longterm()`: 여러 중기 요약을 **텍스트 전용** 압축(중복 병합·엔티티 명명 통일·최종 handoff 상태 보존), 결과 앞에 `<merged_range>` 타임스탬프를 붙임(`long_term_max_tokens=1200`).
- **주입**: 요약된 기억은 `build_static_system_content()`/`build_dynamic_system_content()`를 통해 시스템 프롬프트로 주입 → 프리픽스 캐싱 대상이 됨.
- **해설**: 논문의 "단기(원본 토큰)→중기(요약)→장기(블록)" 3층이 (원본 프레임 스트림) → **중기=멀티모달 요약** → **장기=텍스트 압축 블록**으로 구현. 논문 상수 T_s=100/M=5/L=15는 코드에서 `chunk`/`compress_every_n_chunks`/버퍼 상한 등으로 파라미터화(정확 기본값은 설정 로딩 확인 필요 — **추정**).

### 3.4 실시간 루프 — `webui/…/server.py` + `video_processor.py`

`server.py:offer()` (WebRTC 협상):
```python
async def offer(request):
    params = await request.json()
    offer_sdp = RTCSessionDescription(sdp=params["sdp"], type=params["type"])
    session_id = params.get("session_id", "default")
    session = get_or_create_session(session_id)
    session_vlm = session["vlm_service"]
    pc = RTCPeerConnection(configuration=RTCConfiguration(iceServers=[...stun...]))

    @pc.on("track")
    def on_track(track):
        if track.kind == "video":
            processor_track = VideoProcessorTrack(
                relay.subscribe(track), session_vlm,
                text_callback=session_callback,
                background_service=background_service)
            pc.addTrack(processor_track)
```
- RTSP 소스는 `RTSPVideoTrack(rtsp_url)`를 `relay.subscribe()` 후 동일한 `VideoProcessorTrack`으로 연결 → 웹캠/IP카메라 **동일 처리 경로**.
- `VideoProcessorTrack.recv()`의 1Hz 게이트/지연 관리:
```python
time_since_last = now - self._last_process_time
need_conversion = (time_since_last >= interval_sec) or (self.frame_count == 1)
...
if max_latency > 0 and frame_latency > max_latency and frame.pts is not None:
    # PTS×time_base로 프레임 나이 계산 → 초과 프레임 드롭(최대 100프레임 안전루프)
```
- **해설**: 논문 "1Hz 샘플링 + 절대 멈추지 않는 실시간 루프"가 `process_interval_seconds=1.0` 게이트와 지연 초과 프레임 드롭으로 구현. 원본 프레임은 zero-copy passthrough로 되돌려 UI 스트림 유지, VLM 결과는 별도 `text_callback`으로 전달(추론과 미디어 경로 분리).

### 3.5 WebSocket 세션 라우팅 — `server.py`

- 라우트: `/offer`(WebRTC), `/ws`(WebSocket), `/api/session/cleanup`, `/api/rtsp/{start,stop,status}`, `/models`, `/detect-services`, 정적 `/images`·`/favicon`, ASR/TTS 라우트 setup.
- 전역 세션 상태: `sessions`, `session_websockets`(session→ws set), `ws_to_session`, `session_peer_connections`, `rtsp_tracks`.
- `websocket_handler`가 처리하는 제어 메시지: `update_prompt`, `update_model`, `update_processing`, `update_frames_per_batch`, `update_background_config`, `set_debug`, `reset_session`, `cleanup_session`, `update_max_latency`.
- `get_session_callback()`이 VLM 응답을 받아 **위임 감지 후** `{"type":"vlm_response",...}` JSON을 `send_to_session()`으로 해당 세션 소켓들에 브로드캐스트.
- **해설**: 프런트엔드가 실시간으로 프롬프트/모델/배치/지연 상한을 갱신 가능한 **멀티세션** 서버. 각 세션은 독립 `VLMService`+백그라운드 프로세서+피어커넥션을 가지며, `cleanup_session()`에서 소켓·PC·활성요청을 순차 정리.

### 3.6 백그라운드 위임 — `background_model.py` + `background-agent/codex_api/main.py`

- 감지: `parse_delegation()`이 `<delegation>` 태그 또는 `<|background_call|>` 마커에서 질문을 추출해 `DelegationRequest` 생성. `handle_foreground_response()`는 빈/마커-only 질문 필터링 후 태스크 ID 발급, **현재 프레임 스냅샷**과 함께 `asyncio.create_task(_run_delegation_task())` 기동, 사용자엔 즉시 **플레이스홀더** 반환(실시간성 유지).
- 실행: `_run_delegation_task()`가 프레임+질문을 `POST /v1/solve`로 백그라운드 에이전트에 전송, `asyncio.wait_for(..., timeout_seconds=600)`로 감쌈. 결과 정규화(`_normalize_background_result()`: thinking/summary 태그 추출), 리치 콘텐츠(HTML/차트) 분리, 세션 콜백으로 lifecycle 통지.
- 백엔드 스키마(`codex_api/main.py`):
```python
class SolveRequest(BaseModel):
    session_id: str; task_id: str; question: str
    foreground_text: str = ""; frames: list[FrameInput] = []
    max_subagents: int | None = None; timeout_seconds: float | None = None

class SolveResponse(BaseModel):
    status: Literal["completed","failed","timeout"]
    text: str; thread_id: str | None; usage: dict | None
    duration_ms: float; events_digest: dict; error: str | None
```
`_solve_with_codex()`: codex CLI 탐색 → 프레임 임시저장 → 워크스페이스/이미지경로/에이전트 상한으로 커맨드 조립 → `CODEX_HOME`으로 서브프로세스 실행 → stdout(JSONL)·stderr 동시 판독(`JsonlState.ingest()`) → 타임아웃·출력크기 상한 강제, 실패 시 프로세스 그룹 종료.
- **해설**: 논문의 "고정·모델-무관 텍스트 프로토콜 배경 브릿지(타임아웃·취소·병합)"가 정확히 대응. `/v1/solve`의 텍스트 in/out 계약 덕에 백그라운드 실행기를 **codex 외 다른 에이전트로 교체 가능**(model-agnostic). `status ∈ {completed,failed,timeout}`가 타임아웃 처리를, `handle_foreground_response`의 즉시 플레이스홀더+완료 후 `notify_session_json`이 **병합(merge)**을 구현.

## 4. 의존성 / 환경 요구사항

- **GPU**: 최소 메인 VLM(8B) 1장 + 요약 VLM(Qwen3-VL-4B) — `MAIN_GPU`, `SUMMARY_GPU` 환경변수로 지정, `--gpu-memory-utilization 0.9`. ASR/TTS까지 all 모드면 추가 자원 필요.
- **핵심 패키지**: `vllm`(OpenAI API server, prefix caching), `aiohttp`, `aiortc`(+ av/pyav, WebRTC), `openai`(AsyncOpenAI), `Pillow`(프레임 인코딩), `fastapi`/`uvicorn`(background-agent), `pydantic`. 각 서비스 `pyproject.toml` 및 `uv.lock` 존재(`uv` 사용).
- **모델 가중치**: `./install/download-models.sh --all` → 기본 경로 `/tmp/models/JoyAI-VL-Interaction-Preview`(메인), 요약용 `Qwen3-VL-4B-Instruct`. 미존재 시 `start_model.sh`가 즉시 에러.
- **외부 바이너리**: `codex` CLI(background-agent, `CODEX_HOME`=`services/background-agent/codex-home/`). 웹 검색/서브에이전트 옵션은 `background-agent.env`로 설정.
- **인증서**: WebUI는 HTTPS(WebRTC 필수) — `webui/scripts/generate_cert.sh`로 자체서명 인증서 생성 후 `:8099`.
- **포트**: 7060/8065/8070(webinfer), 8099(webui), 8079(background-agent), 8991~8994(tts/asr). 방화벽/충돌 확인.

## 5. 최소 재현(Minimal Repro) 가능 여부와 경로

- **가능(권장 최소 경로 = `minimal` 모드)**:
  1. `./install/install.sh`로 의존성 설치, `./install/download-models.sh --all`로 가중치 다운로드.
  2. `./services/webui/scripts/generate_cert.sh`로 TLS 인증서 생성.
  3. `./services/scripts/run.sh minimal` 실행 → webinfer(메인 VLM :7060 + 요약 VLM :8065 + 어댑터 :8070) 기동·헬스체크 후 WebUI 포그라운드.
  4. 브라우저에서 `https://127.0.0.1:8099` 접속, 웹캠 허용 → 실시간 침묵/응답 확인.
- **위임까지 포함하려면 `all` 모드**: `./services/scripts/run.sh all` (필요 시 `START_ASR=0 START_TTS=0`로 음성 서비스 생략, `START_BACKGROUND_AGENT=1` 유지). codex CLI + `CODEX_HOME` 준비 필요.
- **GPU 없는 축소 재현**: 메인 8B vLLM 서빙이 병목 — 저사양이면 `MODEL_PATH`를 소형 OpenAI-호환 VLM으로 교체하고 `AdapterConfig.main_api_base`만 맞추면 실시간 루프/WebRTC/요약/위임 배선 자체는 검증 가능(모델 품질은 별개). `frames_per_batch`·`process_interval_seconds`·`max_frame_latency`는 WebSocket 제어 메시지로 런타임 조정.
- **검증 팁**: `run.sh`의 헬스 URL(`/v1/models`, `/health`)로 각 컴포넌트 기동 확인. 위임 경로는 `POST :8079/v1/solve`에 `SolveRequest` JSON을 직접 던져 단위 확인 가능.

---

### 참고: 문서 정합성 / 미확인 항목
- 특수 토큰 표기 차이(`</delegate>` ↔ `</delegation>`/`<|background_call|>`)와 메모리 상수(T_s=100/M=5/L=15)의 코드 기본값은 어댑터 설정 로딩부에서 최종 확인 요(본문에서 **추정** 표기).
- 상기 코드 발췌는 공식 저장소 `main` 브랜치의 실제 파일 경로(`services/…`)에 근거하며, 일부 함수 본문은 시그니처/핵심 라인 기준으로 인용했다.

---

**저장 경로**: `C:/Users/wagra/claude/code/output/joyaivl_2606.14777/04_code.md`
