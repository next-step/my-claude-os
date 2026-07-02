# 구현 코드 분석: LiveEdit — Towards Real-Time Diffusion-Based Streaming Video Editing

- **저장소**: https://github.com/cp-cp/LiveEdit (**공식** 코드, ECCV 2026, Apache-2.0)
  - 사전학습 가중치: `cp-cp/LiveEdit` (HF, `ar-forcing_002000.pt`), 베이스 `Wan-AI/Wan2.1-T2V-1.3B`
  - 프로젝트 페이지: https://live-edit.github.io
  - 기반 코드베이스: **Self-Forcing**(guandeh17/Self-Forcing) + **Wan2.1**(Wan-Video) 포크
- **언어 / 핵심 프레임워크**: Python 99.7% / PyTorch, Diffusion Transformer(Wan2.1-T2V-1.3B), flash-attn, FSDP, HuggingFace, LMDB, (TensorRT 옵션)
- **실행 진입점**:
  - 추론: `inference-mm.py` (셸 래퍼 `infer-local-ar-forcing.sh`, `infer-token-pruning.sh`)
  - 비스트리밍/양방향 추론: `inference-mm-diffusion-pipeline.py`
  - 학습: `train.py` (3단계 셸 `train-mm-bid-diffusion.sh` → `train-mm-ar-diffusion.sh` → `train-mm-ar-forcing.sh`)

> 참고: 논문(01_analysis.md)은 "공식 코드 미기재"로 적었으나, 실제로는 위 공식 저장소가 공개되어 있어 이를 1차 소스로 분석함.

## 1. 디렉토리 구조 (핵심만)

```
LiveEdit/
├─ inference-mm.py                      # 스트리밍(인과) 추론 진입점
├─ inference-mm-diffusion-pipeline.py   # 양방향(Stage1) 추론
├─ train.py                             # 통합 학습 런처
├─ infer-local-ar-forcing.sh            # 4-step 인과 추론 (마스크 캐시 OFF)
├─ infer-token-pruning.sh               # 마스크 캐시/토큰 프루닝 추론
├─ train-mm-bid-diffusion.sh            # Stage1 Foundation Tuning
├─ train-mm-ar-diffusion.sh            # Stage2 Teacher Forcing(인과 전환)
├─ train-mm-ar-forcing.sh              # Stage3 DMD 증류
├─ configs/
│   ├─ wan_mm-bid-diffusion.yaml        # Stage1 설정
│   ├─ wan_mm-ar-diffusion.yaml         # Stage2 설정
│   ├─ wan_mm-ar-forcing-local.yaml     # Stage3/추론 설정(캐시 OFF)
│   └─ wan_mm-token-pruning.yaml        # 마스크 캐시/프루닝 추론 설정
├─ model/
│   ├─ base.py            # SelfForcingModel 베이스
│   ├─ mm_diffusion.py    # Stage1 양방향 편집 모델(MSE)
│   ├─ mm_causvid.py      # Stage2 인과(CausVid 류) 모델
│   ├─ mm_dmd.py          # Stage3 DMD(Real/Fake score) 증류 ★
│   ├─ mm_regression.py / ode_regression.py / sid.py / gan.py …
├─ pipeline/
│   ├─ mm_inference.py                  # 스트리밍 추론 파이프라인(KV 캐시)
│   ├─ causal_inference.py              # 인과 추론 + 마스크 캐시/토큰 프루닝 ★
│   ├─ causal_diffusion_inference.py
│   ├─ bidirectional_(diffusion_)inference.py
│   └─ self_forcing_training.py
├─ wan/modules/
│   ├─ model.py           # 원본 Wan DiT
│   ├─ causal_model.py     # 인과 어텐션/블록 마스크/KV 캐시 ★
│   ├─ attention.py, vae.py, tiny_vae.py, t5.py, clip.py …
├─ trainer/   (diffusion.py, distillation.py, mm.py, ode.py, gan.py)
├─ utils/     (wan_wrapper.py, scheduler.py, loss.py, dataset.py, lmdb.py, distributed.py)
├─ demo_utils/ (vae*, taehv.py, memory.py)   # 실시간 디코딩/메모리 유틸
└─ scripts/   (create_lmdb_*.py, generate_ode_pairs.py)
```

## 2. 논문 ↔ 코드 매핑 표

| 논문 개념 | 코드 위치(파일:함수/클래스) | 설명 |
|---|---|---|
| Stage 1 — Foundation Tuning(양방향 편집 baseline, MSE) | `model/mm_diffusion.py` (`MMDiffusion`), config `wan_mm-bid-diffusion.yaml`, `train-mm-bid-diffusion.sh` | 원본 latent + 노이즈 latent를 채널 concat 후 flow/MSE 손실로 양방향 DiT 학습 |
| 입력 채널 16→32 확장(source latent concat) | `model/mm_dmd.py:_expand_input_layer` / `pipeline/mm_inference.py:_expand_input_layer` | `Conv3d(in_channels=32)` 신규 생성, `weight[:, :16]`에 기존 가중치 복사·17~32는 0 초기화(소스 비디오 주입) |
| Stage 2 — Teacher Forcing(양방향→인과), chunk-wise causal mask | `wan/modules/causal_model.py:CausalWanModel._prepare_blockwise_causal_attn_mask`, config `wan_mm-ar-diffusion.yaml` | "block-wise causal mask"가 현재 chunk 끝까지만 attend; `num_frame_per_block=3` latent-frame 단위 |
| Stage 3 — DMD(Distribution Matching Distillation, 4-step) | `model/mm_dmd.py:MMDMD` (`compute_distribution_matching_loss`, `generator_loss`, `critic_loss`), `train-mm-ar-forcing.sh` | frozen Real Score − trainable Fake Score 차이로 분포정합 gradient, MSE 형태로 역전파 |
| 샘플링 timestep [≈0,250,500,750] / 4 NFE | `configs/wan_mm-token-pruning.yaml: denoising_step_list: [1000,750,500,250]` + `warp_denoising_step: true` | 4개 denoising step, CFG 불필요(`guidance_scale`는 score 모델용) |
| AR-지향 마스크 캐시 — L₂로 편집/정적 영역 식별 | `pipeline/causal_inference.py:_compute_mask_from_generated` | `diff=(generated_latent−source_latent).pow(2).mean(dim=2)`로 L₂², min-max 정규화해 importance 맵 산출 |
| ~70% 토큰 프루닝(편집 영역만 유지) | `pipeline/causal_inference.py:_compute_mask_from_importance` + `adaptive_patch_ratio: 0.3` | 상위 30%만 keep(`kthvalue`로 임계 τ 결정) → 약 70% prune |
| 캐시는 Self-Attention 층에만 | config `internal_pruning_layers: ["self_attn"]`, `use_internal_pruning: true` | FFN 제외(논문: FFN 캐시 시 고주파 열화) |
| chunk-by-chunk 인과 추론 + KV 캐시 재사용 | `pipeline/mm_inference.py:inference`, `wan/modules/causal_model.py:CausalWanSelfAttention.forward(kv_cache=…)` | 프레임 그룹마다 KV 캐시 갱신·롤링 eviction·sink token 유지 |
| 실시간 디코딩(저지연) | `demo_utils/vae*.py`, `demo_utils/taehv.py` (TAE-HV), `wan/modules/tiny_vae.py` | 경량 VAE/TensorRT 경로로 프레임당 디코딩 가속 |

## 3. 데이터 흐름 추적

추론(스트리밍, `inference-mm.py` → `pipeline/causal_inference.py` / `mm_inference.py`):

1. **입력**: source video + text instruction(`test_cases/test.json`, `test.mp4`).
2. **전처리**: T5 텍스트 인코딩(`wan/modules/t5.py`), VAE로 source video → `source_latent`(16ch). 노이즈 latent와 채널 concat → 32ch DiT 입력.
3. **chunk 루프**(`num_frame_per_block=3` latent frame 단위):
   - 직전 chunk 결과 `generated_latent`와 `source_latent`로 `_compute_mask_from_generated` → importance 맵.
   - `_compute_mask_from_importance`(`adaptive_patch_ratio=0.3`) → 상위 30% keep(편집 영역), 나머지 ~70% prune.
   - **4-step denoise**(`denoising_step_list=[1000,750,500,250]`): 각 step에서 `generator(noisy_input, kv_cache, current_start=...)` 호출. prune된 토큰은 Self-Attention에서 `unpruned_fill_strategy="prev_step"`로 이전 step/chunk 캐시 feature 재사용.
   - KV 캐시·cross-attn 캐시 갱신(`current_start_frame * frame_seq_length`), 롤링 eviction + sink token.
4. **출력**: 편집된 latent → VAE 디코딩(`demo_utils/vae*`) → 프레임 출력(스트리밍). 12.66 FPS / ~79ms per 3-frame chunk.

학습(3단계): Stage1 양방향 MSE(`MMDiffusion`) → Stage2 인과 mask 적용 teacher forcing(`MMCausVid`/`causal_model`) → Stage3 DMD(`MMDMD`, Real frozen + Fake trainable, `dfake_gen_update_ratio=5`).

## 4. 핵심 코드 발췌 + 해설

**(1) DMD 분포정합 gradient — `model/mm_dmd.py`**
```python
grad = (pred_fake_image - pred_real_image)
if normalization:
    p_real = (estimated_clean_image_or_video - pred_real_image)
    normalizer = torch.abs(p_real).mean(dim=[1, 2, 3, 4], keepdim=True)
    grad = grad / normalizer
dmd_loss = 0.5 * F.mse_loss(
    original_latent.double(),
    (original_latent.double() - grad.double()).detach(), reduction="mean")
```
해설: Fake/Real score 예측 차이 `(fake−real)`를 정규화한 뒤, generator latent에서 빼는 형태로 MSE 손실 구성 → 분포정합(reverse-KL 근사) gradient를 표준 MSE 역전파로 흘림. `y=conditional_dict["source_latent"]`로 편집 조건(소스) 주입.

**(2) 입력 채널 확장(소스 비디오 concat) — `model/mm_dmd.py:_expand_input_layer`**
```python
new_proj = torch.nn.Conv3d(in_channels=32, ...)
new_proj.weight[:, :16].copy_(old_proj.weight)   # 기존 16ch 가중치 보존
# 채널 17~32 = 0 초기화 → source latent 입력 경로
```
해설: 양방향 baseline의 patch-embed를 16→32ch로 확장해 source latent를 추가 주입. 신규 채널 0초기화로 학습 초기 안정성 확보.

**(3) chunk-wise causal attention mask — `wan/modules/causal_model.py`**
```python
frame_indices = torch.arange(0, total_length,
                             step=frame_seqlen * num_frame_per_block, device=device)
# mask: (kv_idx < ends[q_idx]) | (q_idx == kv_idx)
```
해설: 각 query는 "현재 chunk 끝(ends) 이전" 토큰까지만 attend → 미래 누설 차단(인과화). `num_frame_per_block=3`로 3 latent-frame을 한 chunk로 묶음. KV 캐시는 sink token 고정 + 롤링 eviction.

**(4) AR-지향 마스크 캐시(L₂ → 프루닝) — `pipeline/causal_inference.py`**
```python
def _compute_mask_from_generated(self, generated_latent, source_latent):
    diff = (generated_latent - source_latent).pow(2).mean(dim=2)        # L2^2
    importance = (diff - diff.min()) / (diff.max() - diff.min() + 1e-8)
    low_importance_mask = self._compute_mask_from_importance(importance)

def _compute_mask_from_importance(self, importance_map):
    keep_num = int(num_tokens * self.adaptive_patch_ratio)              # 0.3 → 30% keep
    threshold_value = torch.kthvalue(importance_frame,
                                     num_tokens - keep_num + 1).values  # 임계 τ
    high_importance_frame = (importance_frame >= threshold_value)       # 편집 영역
    low_importance_mask_flat[b, f] = ~high_importance_frame             # 정적 영역=캐시
```
해설: 직전 chunk의 편집 강도(L₂²)로 편집/정적 토큰을 이진 분리. 상위 30%(편집)만 재계산, 하위 ~70%(정적)는 Self-Attention 캐시 feature 재사용(`unpruned_fill_strategy="prev_step"`). `internal_pruning_layers=["self_attn"]`로 FFN 제외(논문 ablation 일치).

## 5. 의존성 / 환경 요구사항 (실행에 필요한 것)

- **OS/HW**: Linux + NVIDIA GPU 권장. 추론은 단일 GPU 가능(논문 학습은 A100×8). flash-attn 빌드 필요 → CUDA toolkit 필수.
- **환경 구성**:
  ```bash
  conda create -n liveedit python=3.10 -y && conda activate liveedit
  pip install -r requirements.txt
  pip install flash-attn --no-build-isolation
  ```
- **가중치 다운로드**:
  ```bash
  huggingface-cli download Wan-AI/Wan2.1-T2V-1.3B \
    --local-dir-use-symlinks False --local-dir wan_models/Wan2.1-T2V-1.3B
  mkdir -p checkpoints/liveedit
  huggingface-cli download cp-cp/LiveEdit ar-forcing_002000.pt \
    --local-dir checkpoints/liveedit
  ```
- **주요 패키지**: torch, flash-attn, diffusers/transformers 계열, lmdb, einops, imageio/opencv(비디오 I/O), wandb(학습 로깅), (옵션) torch2trt/TensorRT(`demo_utils/vae_torch2trt.py`).
- **핵심 설정값**(`configs/wan_mm-token-pruning.yaml`): `denoising_step_list:[1000,750,500,250]`, `num_frame_per_block:3`, `num_frames:81`, `image_or_video_shape:[1,21,16,60,104]`, `adaptive_patch_ratio:0.3`, `internal_pruning_layers:["self_attn"]`, `guidance_scale:3.0`, `v2v:true`.

## 6. 최소 재현(Minimal Repro) 가능 여부와 경로

- **가능**(추론). 단, Linux+NVIDIA GPU와 flash-attn 빌드가 전제. Windows 네이티브에서는 flash-attn/일부 CUDA 커널 빌드 이슈 가능 → WSL2 또는 Linux 권장.
- **최소 재현 절차(스트리밍 추론, 마스크 캐시 OFF — 가장 단순)**:
  1. 위 5장 환경 구성 + 가중치 2종 다운로드.
  2. `configs/wan_mm-ar-forcing-local.yaml`의 `generator_ckpt`를 `checkpoints/liveedit/ar-forcing_002000.pt`로, Wan 경로를 `wan_models/Wan2.1-T2V-1.3B`로 지정.
  3. 실행:
     ```bash
     bash infer-local-ar-forcing.sh
     # 내부: python inference-mm.py --config_path configs/wan_mm-ar-forcing-local.yaml
     ```
  4. 입력 샘플 `test_cases/test.mp4` + `test_cases/test.json`(instruction)으로 편집 결과 비디오 생성.
- **효율(마스크 캐시) 재현**:
  ```bash
  bash infer-token-pruning.sh
  # python inference-mm.py --config_path configs/wan_mm-token-pruning.yaml --save_mask
  ```
  `save_mask: True`로 두면 `videos/masks`에 편집/정적 마스크 시각화 저장 → 논문의 ~70% 프루닝 거동 확인.
- **학습 재현(고비용)**: A100급 다중 GPU + Ditto-1M 필터링 20K 쌍(LMDB 변환: `scripts/create_lmdb_*.py`) 필요. Stage1→2→3 순서로 셸 실행, 각 단계 ckpt를 다음 config(`<CKPT_FROM_STAGE1/2>`)에 연결.
- **주의(`/code-run`용)**:
  - config 내 `<CKPT_FROM_STAGE1>`, `<CKPT_FROM_STAGE2>`, `<YOUR_DATA_PATH>`, `WANDB_*` 플레이스홀더는 실행 전 반드시 치환.
  - flash-attn 미빌드 환경에서는 추론도 실패하므로 GPU/CUDA 우선 확인.
  - 단일 입력 1샷 추론이 가장 빠른 검증 경로(`test_cases/` 제공 샘플 사용).

---
출력 경로: `C:/Users/wagra/claude/code/output/04_code.md`
