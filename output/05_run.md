# 실행 리포트: LiveEdit — Towards Real-Time Diffusion-Based Streaming Video Editing

> 본 리포트는 `output/04_code.md`의 핵심 메커니즘을 **토이 입력으로 1-step 동작만** 증명한
> 최소 재현 데모의 실제 실행 결과입니다. 대규모 학습/추론은 수행하지 않았습니다(스킬 안전 규칙 준수).

## 환경 (OS / Python / 핵심 패키지 버전)

- **OS**: Windows 10 Education (10.0.19045), native (WSL/Linux 아님)
- **Python**: 3.10.9 (Anaconda, `C:\Users\wagra\anaconda3\python.exe`)
- **torch**: 1.12.1 (**CPU only**, `cuda=False`)
- **numpy**: 1.23.5
- **사용하지 않은 것**: flash-attn, Wan2.1-T2V-1.3B 가중치, LiveEdit 체크포인트(`ar-forcing_002000.pt`), GPU
  → 이들은 **전체 LiveEdit 추론**에만 필요하며, 본 데모는 순수 PyTorch CPU 연산만으로 핵심 알고리즘을 재현.

## 재현 명령 (복붙용 한 블록)

```powershell
# Windows PowerShell. Anaconda Python 3.10 사용.
cd C:\Users\wagra\claude\code\output\run
$env:PYTHONIOENCODING="utf-8"; $env:PYTHONUTF8="1"   # 콘솔 cp949에서 한글/≈ 출력용
& "C:\Users\wagra\anaconda3\python.exe" run_demo.py
# (의존성은 이미 설치됨. 새 환경이면: pip install -r requirements.txt)
```

- 실행 파일: `C:/Users/wagra/claude/code/output/run/run_demo.py`
- 의존성 명세: `C:/Users/wagra/claude/code/output/run/requirements.txt`
- 총 실행 시간: **약 0.75초** (CPU)

## 실제 실행 로그 (발췌)

```
LiveEdit 최소 재현 데모 (CPU / 토이 입력 / 학습 없음)
torch=1.12.1, cuda=False

(1) 입력 채널 16->32 확장  (_expand_input_layer)
old(16ch) 출력 shape         : (1, 8, 4, 4, 4)
new(32ch) 출력 shape         : (1, 8, 4, 4, 4)
확장 직후 |old-new| max diff  : 0.000e+00  (≈0 이면 호환 보존 OK)
source 채널 활성 후 변화 max  : 2.151e+00  (>0 이면 주입 경로 작동)
PASS: 16->32 확장이 기존 동작 보존 + source 주입 경로 추가

(2) chunk-wise causal attention mask  (_prepare_blockwise_causal_attn_mask)
총 토큰 L=12, block size=6 (=frame_seqlen*num_frame_per_block), blocks=2
future-block attention(누설) 수 : 0  (0 이어야 인과성 성립)
전체 대비 허용 비율            : 75.0% (block 하삼각 구조)
어텐션 출력 shape             : (1, 12, 8)
미래 토큰 변경 시 첫 block 변화: 0.000e+00 (≈0 이면 인과 OK)
PASS: block-wise causal mask가 미래 누설 차단

(3) L2 마스크 캐시 -> 토큰 프루닝  (_compute_mask_from_generated)
frame당 토큰수=64, keep_num(상위30%)=19
재계산(편집) 토큰=57, 캐시(정적) 토큰=135, 총=192
prune 비율=70.3%  (논문 ~70% 목표)
편집한 좌상단 영역의 keep 비율: 100.0% (편집영역=재계산 분류)
PASS: L2 중요도 -> 상위30% 재계산 / ~70% 캐시 프루닝

(4) DMD 분포정합 손실  (compute_distribution_matching_loss)
dmd_loss = 0.773407  (스칼라)
d(loss)/d(original_latent) norm = 0.013741  (>0: gradient 전파)
fake==real 일 때 loss = 0.000e+00  (≈0: 분포 일치 시 무손실)
PASS: (fake-real) 차이를 MSE 형태로 역전파, 분포 일치 시 0

(5) 미니 스트리밍 chunk 루프 (데이터 흐름 모사)
  chunk 0: 4-step denoise 완료, 적용 prune=0.0%, out shape=(1, 3, 16, 8, 8)
  chunk 1: 4-step denoise 완료, 적용 prune=70.3%, out shape=(1, 3, 16, 8, 8)
  chunk 2: 4-step denoise 완료, 적용 prune=70.3%, out shape=(1, 3, 16, 8, 8)
3 chunk 스트리밍 완료, chunk>=1 평균 prune=70.3%, 소요 1.0ms (CPU 토이)
PASS: source 주입 -> 마스크 캐시 -> 4-step denoise -> chunk 출력 흐름 작동

ALL DEMOS PASSED
4개 핵심 메커니즘 + 스트리밍 흐름을 토이 입력으로 검증 완료.
```

## 결과 해석 (출력이 논문 동작과 일치하는가)

`04_code.md`가 지목한 4개 핵심 코드 블록을 토이 텐서로 떼어내 재현했고, 모두 논문이 기술한
거동과 일치하는 정량 결과를 냈다.

| # | 메커니즘 | 04_code.md 근거 | 데모가 증명한 것 | 논문 일치 |
|---|---|---|---|
| 1 | 입력 채널 16→32 확장 | 발췌(2) `_expand_input_layer`: `weight[:,:16]` 복사 + 17~32 0초기화 | 확장 직후 출력이 기존 16ch conv와 **완전 동일**(diff=0.0e0) → 초기 안정성 보존, source 채널 활성 시 출력 변화(2.15)로 **주입 경로 작동** | ✅ |
| 2 | chunk-wise causal mask | 발췌(3) `num_frame_per_block=3`, "현재 chunk 끝까지만 attend" | 미래 block으로의 누설 **0건**, 미래 토큰을 바꿔도 과거 block 출력 **불변(0.0e0)** → 인과성 성립. block 하삼각 구조로 허용 비율 75% | ✅ |
| 3 | L2 마스크 캐시 / ~70% 프루닝 | 발췌(4) `_compute_mask_from_generated`, `adaptive_patch_ratio=0.3`, `kthvalue` τ | L2² 중요도 상위 30%만 keep → **prune 70.3%** (논문 "~70%"와 일치), 실제 편집 영역(좌상단)이 **100% keep**으로 분류 | ✅ |
| 4 | DMD 분포정합 손실 | 발췌(1) `grad=(fake-real)` 정규화 후 MSE | 스칼라 손실 + generator latent로 **gradient 전파**(norm>0), `fake==real`이면 손실 **정확히 0** → 분포 일치 시 무손실 | ✅ |
| 5 | 스트리밍 데이터 흐름 | 3장 chunk 루프 + 4-step denoise `[1000,750,500,250]` | source 주입→직전 chunk 기반 마스크 캐시→4-step denoise→chunk 출력의 전체 파이프라인이 끊김 없이 동작, chunk≥1에서 70.3% 프루닝 적용 | ✅ |

정량적으로 (3) prune 70.3% ≈ 논문 ~70%, (1)·(2)의 0.0e0 동치 검증, (4) 분포 일치 시 손실 0 등은
모두 알고리즘의 핵심 불변식(invariant)을 그대로 재현한 것이다.

## 알려진 이슈 / 다음 단계 (전체 학습/추론으로 확장하려면)

**본 데모가 증명하지 못하는 것(정직한 한계):**
- 실제 `test_cases/test.mp4` 영상 편집 결과(품질, 12.66 FPS, 79ms/3-frame chunk 등 성능 수치)는
  재현하지 않았다. 본 데모는 **알고리즘 메커니즘의 정합성**만 토이 텐서로 검증한 것이지,
  학습된 가중치를 통한 실제 비디오 편집 출력이 아니다.
- 데모의 attention/conv는 단순화된 토이 모듈이며 실제 Wan2.1 DiT가 아니다.

**전체 LiveEdit 추론을 돌리려면 갖춰야 할 필요조건:**
1. **OS/HW**: Linux(또는 WSL2) + NVIDIA GPU. 현재 환경은 Windows native + CPU-only torch라
   공식 추론(`inference-mm.py`) 실행 불가.
2. **flash-attn 빌드**: CUDA toolkit 필요. `pip install flash-attn --no-build-isolation`.
   Windows native에서는 빌드 이슈가 잦아 WSL2/Linux 권장.
3. **가중치 2종 다운로드**:
   - 베이스: `Wan-AI/Wan2.1-T2V-1.3B` → `wan_models/Wan2.1-T2V-1.3B`
   - LiveEdit: `cp-cp/LiveEdit` `ar-forcing_002000.pt` → `checkpoints/liveedit`
4. **config 치환**: `wan_mm-ar-forcing-local.yaml`의 `generator_ckpt`, Wan 경로,
   그리고 `<CKPT_FROM_STAGE1/2>`, `<YOUR_DATA_PATH>`, `WANDB_*` 플레이스홀더 치환.
5. **실행**: `bash infer-local-ar-forcing.sh` (스트리밍 4-step 추론) 또는
   `bash infer-token-pruning.sh --save_mask` (마스크 캐시/~70% 프루닝 시각화).
6. **학습 재현(고비용)**: A100급 다중 GPU + Ditto-1M 필터링 20K 쌍(LMDB 변환) →
   Stage1(`train-mm-bid-diffusion.sh`)→Stage2→Stage3(DMD) 순. 현재 환경에서는 불가.

---
- 데모 디렉토리: `C:/Users/wagra/claude/code/output/run/`
- 실행 스크립트: `C:/Users/wagra/claude/code/output/run/run_demo.py`
- 의존성: `C:/Users/wagra/claude/code/output/run/requirements.txt`
- 출력 경로: `C:/Users/wagra/claude/code/output/05_run.md`
