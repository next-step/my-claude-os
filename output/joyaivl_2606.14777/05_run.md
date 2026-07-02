# 실행 준비 리포트: JoyAI-VL-Interaction

> 이 리포트는 **원본 공식 레포를 사용자가 자기 터미널에서 직접 실행**하도록 준비한 가이드입니다.
> 클로드는 이 명령들을 대신 실행하지 않았습니다 — 아래 "복붙용 터미널 명령"을 님 터미널에 붙여넣어 실행/관찰하세요.
> (이전 버전의 `run/run_demo.py`는 원본이 아니라 04_code.md 문서 동작을 재현한 **토이 데모**였습니다. 새 방식에서는 원본 레포 실행이 정본이며, 토이 데모는 GPU가 없을 때의 오프라인 스모크 테스트 fallback으로만 남겨둡니다.)

## 대상 (원본 레포 / 고정 커밋 / 진입점)
- **레포**: https://github.com/jd-opensource/JoyAI-VL-Interaction (공식 · JD.com `jd-opensource`, 코드 Apache-2.0)
- **고정 커밋**: `9d07596` (04_code.md 분석 시점 트리; 재현성 위해 이 커밋으로 checkout 권장)
- **진입점**: `services/scripts/run.sh minimal` → 헬스체크 후 WebUI 포그라운드, 브라우저 `https://127.0.0.1:8099`
- **모델 가중치**: `install/download-models.sh --all` → 메인 `JoyAI-VL-Interaction-Preview`(8B) + 요약 `Qwen3-VL-4B-Instruct`

## 환경 요구 (OS / Python / GPU / 디스크·다운로드)
- **OS**: Linux 권장 (서비스가 bash 스크립트 + vLLM). Windows는 **WSL2(Ubuntu)** 사용 — 네이티브 Windows에서는 vLLM/aiortc 빌드 이슈.
- **Python**: 3.12+, CUDA 12.x
- **GPU**: 메인 8B VLM + 요약 4B VLM 서빙 → 고사양 VRAM 필요(단일 고사양 GPU 또는 텐서병렬). `--gpu-memory-utilization 0.9`
- **디스크/다운로드**: 8B + 4B 가중치 다운로드(수십 GB, 수십 분). 네트워크·HF 계정 필요할 수 있음.
- **추가**: WebRTC용 HTTPS 자체서명 인증서 필요(`generate_cert.sh`).

## 복붙용 터미널 명령 (clone → env → install → run)
> 아래를 **님의 터미널(Linux/WSL2)** 에 순서대로 붙여넣어 실행하세요. 클로드는 실행하지 않습니다.

```bash
# 0) (Windows면 WSL2 Ubuntu 터미널에서) 작업 폴더로 이동
cd ~/ && mkdir -p joyai && cd joyai

# 1) 원본 레포 clone + 분석 시점 커밋으로 고정(재현성)
git clone https://github.com/jd-opensource/JoyAI-VL-Interaction.git
cd JoyAI-VL-Interaction
git checkout 9d07596        # 실패 시 생략하고 최신 main 사용

# 2) 의존성 설치 (레포 제공 스크립트 사용)
bash install/install.sh

# 3) 모델 가중치 다운로드 (수십 GB — 시간·디스크 확인)
bash install/download-models.sh --all
#    메인: JoyAI-VL-Interaction-Preview(8B) / 요약: Qwen3-VL-4B-Instruct

# 4) WebRTC용 HTTPS 인증서 생성(브라우저 웹캠 필수)
bash services/webui/scripts/generate_cert.sh

# 5) 최소 스택 기동 (메인 VLM:7060 + 요약 VLM:8065 + 어댑터:8070 + WebUI:8099)
bash services/scripts/run.sh minimal
#    → 헬스체크 통과 후 WebUI가 포그라운드로 뜸

# 6) 브라우저에서 접속 후 웹캠 허용
#    https://127.0.0.1:8099   (자체서명 인증서 경고는 '계속' 진행)
```

위임(delegate)까지 포함하려면 `minimal` 대신 `all` 모드:
```bash
START_ASR=0 START_TTS=0 START_BACKGROUND_AGENT=1 bash services/scripts/run.sh all
#    codex CLI + CODEX_HOME 준비 필요(services/background-agent/)
```

## 실행하면 보게 될 것 (성공 판정 기준)
- **헬스체크 통과**: `run.sh`가 `:7060/v1/models`, `:8065/v1/models`, `:8070/health`를 순서대로 OK로 확인 후 WebUI 기동.
- **브라우저 UI**(`https://127.0.0.1:8099`): 웹캠 스트림 + 매초 결정 표시.
- **핵심 관찰 포인트** — 논문 메커니즘이 실제로 보이는가:
  - 질문 없이 그냥 보고 있으면 대부분 **침묵**(`</silence>`) → "질의 전 강제 침묵"(`FORCE_SILENCE_BEFORE_QUERY`).
  - 말을 걸면 **응답**(`</response> …`). 무거운 작업 요청 시 **위임** 플레이스홀더 후 백그라운드 병합(all 모드).
  - 장시간 스트림에서도 지연이 안정적 → prefix caching + 계층 기억 압축.

## 흔한 에러 → 해결
| 증상 | 원인 | 해결 |
|---|---|---|
| `start_model.sh` 즉시 종료 | 모델 경로 없음 | 3)의 `download-models.sh --all` 완료 확인, `MODEL_PATH` 점검 |
| `:7060` 헬스 실패 | VRAM 부족 | `--gpu-memory-utilization` 낮추거나 텐서병렬, 또는 소형 OpenAI-호환 VLM으로 `MODEL_PATH` 교체 후 `main_api_base`만 맞춤 |
| 브라우저 웹캠 안 열림 | HTTPS 인증서 미생성 | 4) `generate_cert.sh` 실행, 브라우저 자체서명 경고 승인 |
| Windows 네이티브 빌드 실패 | vLLM/aiortc | WSL2 Ubuntu에서 실행 |

## 실제 실행 로그 (사용자가 붙여넣을 자리)
> 아직 미실행. 위 명령을 님 터미널에서 돌린 뒤, 출력(헬스체크 결과·UI 동작·결정 로그)을 여기에 붙여주시면 아래 "결과 해석"을 채우겠습니다.

## 결과 해석 (출력이 논문 동작과 일치하는가)
> 사용자 실측 로그 수령 후 작성. (침묵/응답/위임 3지선다, 1Hz 관찰, 장시간 지연 안정성이 04_code.md 매핑표 #3/#4/#9/#11과 일치하는지 대조 예정.)

## GPU 없는 경우 — 오프라인 스모크 테스트 fallback
원본 8B 서빙이 불가하면, 04_code.md에 문서화된 어댑터 동작(메시지 조립·`max_pixels` 다운스케일·`skip_special_tokens`·결정 정규화)만 검증하는 재구현 토이가 `run/`에 있습니다. **이는 원본이 아니라 문서 기반 재구현**이며, 논문 수치 검증용이 아닌 "배선 확인"용입니다:
```bash
python output/joyaivl_2606.14777/run/run_demo.py
```
