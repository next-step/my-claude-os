# 실행 준비 리포트: LiveEdit — Real-Time Diffusion-Based Streaming Video Editing

> 이 리포트는 **원본 공식 레포를 사용자가 자기 터미널에서 직접 실행**하도록 준비한 가이드입니다.
> 클로드는 이 명령들을 대신 실행하지 않았습니다 — 아래 "복붙용 터미널 명령"을 님 터미널에 붙여넣어 실행/관찰하세요.
> (이전 버전의 `run/run_demo.py`는 원본이 아니라 문서 동작을 재현한 **토이 데모**였습니다. 새 방식에서는 원본 레포 추론이 정본입니다.)

## 대상 (원본 레포 / 진입점)
- **레포**: https://github.com/cp-cp/LiveEdit (공식 · ECCV 2026 · Apache-2.0)
- **가중치**: 베이스 `Wan-AI/Wan2.1-T2V-1.3B` + 증류 ckpt `cp-cp/LiveEdit`(`ar-forcing_002000.pt`)
- **진입점**: `bash infer-local-ar-forcing.sh` → `python inference-mm.py --config_path configs/wan_mm-ar-forcing-local.yaml`
- **효율(마스크 캐시/토큰 프루닝) 경로**: `bash infer-token-pruning.sh`

## 환경 요구 (OS / Python / GPU / 디스크)
- **OS**: **Linux + NVIDIA GPU** (flash-attn 빌드 필요). Windows는 **WSL2(Ubuntu)** 권장 — 네이티브 Windows는 flash-attn/CUDA 커널 빌드 이슈.
- **Python**: 3.10, CUDA toolkit 필수(flash-attn 빌드용)
- **GPU**: 추론은 단일 GPU 가능(논문 학습은 A100×8). 실시간 디코딩 목표 12.66 FPS.
- **디스크/다운로드**: Wan2.1-T2V-1.3B(수 GB) + ar-forcing_002000.pt. HF 다운로드.

## 복붙용 터미널 명령 (clone → env → weights → run)
> 아래를 **님의 터미널(Linux/WSL2, NVIDIA GPU)** 에 순서대로 붙여넣어 실행하세요. 클로드는 실행하지 않습니다.

```bash
# 0) 작업 폴더
cd ~/ && git clone https://github.com/cp-cp/LiveEdit.git && cd LiveEdit

# 1) conda 환경 + 의존성
conda create -n liveedit python=3.10 -y
conda activate liveedit
pip install -r requirements.txt
pip install flash-attn --no-build-isolation      # CUDA toolkit 필요, 시간 소요

# 2) 가중치 다운로드 (베이스 Wan2.1 + LiveEdit 증류 ckpt)
huggingface-cli download Wan-AI/Wan2.1-T2V-1.3B \
  --local-dir-use-symlinks False --local-dir wan_models/Wan2.1-T2V-1.3B
mkdir -p checkpoints/liveedit
huggingface-cli download cp-cp/LiveEdit ar-forcing_002000.pt \
  --local-dir checkpoints/liveedit

# 3) config 경로 치환 — configs/wan_mm-ar-forcing-local.yaml 안의
#    generator_ckpt → checkpoints/liveedit/ar-forcing_002000.pt
#    Wan 경로       → wan_models/Wan2.1-T2V-1.3B
#    (에디터로 열어 <CKPT_FROM_STAGE...>/<YOUR_DATA_PATH>/WANDB_* 플레이스홀더 치환)

# 4) 가장 단순한 추론(마스크 캐시 OFF, 4-step 인과) — 제공 샘플 사용
bash infer-local-ar-forcing.sh
#    입력: test_cases/test.mp4 + test_cases/test.json(instruction)
#    출력: 편집된 비디오

# 5) (선택) 효율 경로 — 마스크 캐시/토큰 프루닝 + 마스크 시각화
bash infer-token-pruning.sh      # python inference-mm.py --config ... --save_mask
#    videos/masks 에 편집/정적 마스크 저장 → ~70% 프루닝 거동 확인
```

## 실행하면 보게 될 것 (성공 판정 기준)
- **4)**: `test.mp4`가 instruction대로 편집된 출력 비디오 생성. 로그에 chunk별(3 latent-frame) 4-step denoise 진행.
- **성능 관찰**: ~79ms / 3-frame chunk, 약 12.66 FPS 근처(하드웨어 의존).
- **5) 마스크 시각화**: `videos/masks`의 맵에서 편집 영역(상위 30% keep)과 정적 영역(~70% prune)이 분리되어 보이면 논문의 AR-지향 마스크 캐시가 동작하는 것.

## 흔한 에러 → 해결
| 증상 | 원인 | 해결 |
|---|---|---|
| `flash-attn` 설치 실패 | CUDA toolkit 부재/버전 | CUDA toolkit 설치 후 `--no-build-isolation` 재시도, WSL2 사용 |
| config 실행 중 경로 에러 | `<CKPT_FROM_STAGE*>` 등 플레이스홀더 미치환 | 3) 단계에서 yaml 내 모든 플레이스홀더 실제 경로로 치환 |
| CUDA OOM | VRAM 부족 | `num_frames`/해상도 축소, 더 큰 GPU |
| Windows 네이티브 빌드 실패 | flash-attn | WSL2 Ubuntu에서 실행 |

## 실제 실행 로그 (사용자가 붙여넣을 자리)
> 아직 미실행. 위 명령을 님 터미널에서 돌린 뒤 출력 로그/생성 비디오 경로/FPS를 붙여주시면 아래 "결과 해석"을 채우겠습니다.

## 결과 해석 (출력이 논문 동작과 일치하는가)
> 사용자 실측 로그 수령 후 작성. (4-step DMD 추론·chunk 인과 마스크·~70% 토큰 프루닝이 04_code.md 매핑표와 일치하는지, FPS가 논문 12.66과 부합하는지 대조 예정.)
