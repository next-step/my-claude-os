# 논문 분석: LiveEdit — Towards Real-Time Diffusion-Based Streaming Video Editing

- **원문 링크**: https://huggingface.co/papers/2606.26740 (arXiv: https://arxiv.org/abs/2606.26740 / HTML: https://arxiv.org/html/2606.26740)
- **저자 / 발표처 / 연도**: Xinyu Wang (THU), Chongbo Zhao (THU), Fangneng Zhan (HKUST), Yue Ma (HKUST, 교신저자) / arXiv:2606.26740v1 [cs.CV] / 2026년 6월 25일
- **분야 / 키워드**: 컴퓨터 비전, 확산 모델(Diffusion), 스트리밍 비디오 편집, 실시간 추론, 인과적(causal) 생성, Distribution Matching Distillation, 마스크 캐시

## 1. 한 줄 요약
양방향 확산 비디오 편집 모델을 3단계 증류(distillation)로 4-step 인과 모델로 압축하고 AR-지향 마스크 캐시로 불필요 연산을 줄여, 배경 보존을 유지하면서 12.66 FPS의 실시간 스트리밍 비디오 편집을 달성한 프레임워크 LiveEdit.

## 2. 해결하려는 문제 (Motivation)
스트리밍(프레임 단위, 실시간) 비디오 편집에서 두 가지 핵심 난제를 해결하고자 한다.
- **시간적 안정성**: 편집되지 않은(배경) 영역을 프레임이 흘러가도 일관되게 보존해야 함.
- **실시간 응답성**: 인과적 프레임 단위 처리로 낮은 지연시간(latency)을 확보해야 함.
구체적으로 (1) 양방향 모델을 인과적 추론으로 전환할 때 과거 프레임에 걸쳐 어텐션 가중치가 평탄(uniform)하게 퍼져 구조적 일관성이 깨지는 **어텐션 분포 이동(attention distribution shift)** 문제와, (2) 편집되지 않은 영역까지 모든 공간 토큰을 매 프레임 조밀하게 계산하는 **공간-시간 중복(spatial-temporal redundancy)** 문제를 다룬다.

## 3. 기존 접근의 한계
- 양방향 오프라인 모델(LucyEdit, InsV2V, VideoCoF)은 지연시간이 커서 인터랙티브/실시간 응용에 부적합.
- 기존 스트리밍 생성 기법(StreamV2V, StreamDiffusion 등)은 합성(synthesis)에 치중해 공간적 복원/배경 보존이 떨어짐.
- 생성용 캐시를 편집에 그대로 적용하면 미편집 영역에서 구조 열화와 깜빡임(flickering)이 발생.
- Self-Forcing의 ODE 초기화는 편집 작업에 대해 계산 비용이 과도함.

## 4. 제안 방법 (Method) — 핵심 아이디어와 동작 원리
**(A) 3단계 증류 파이프라인** — 양방향 기반 모델의 편집 능력을 효율적 4-step 인과 모델로 점진 이전.
- **Stage 1 (Foundation Tuning)**: Diffusion Transformer 기반 양방향 편집 baseline 구축. 원본 latent와 노이즈 latent를 채널 방향으로 concat, MSE 손실(‖ϵ−ϵθ^bid(z_t,t,c)‖₂²)로 학습. (9K steps, lr 1e-5, batch 8)
- **Stage 2 (Teacher Forcing)**: 양방향→인과 구조로 전환. 미래 토큰 접근을 막는 chunk-wise causal attention mask 적용(chunk = latent 3프레임). Stage 1의 구조적 prior 유지. (20K steps)
- **Stage 3 (DMD, Distribution Matching Distillation)**: 생성을 4 denoising step으로 압축. Stage 2 가중치에서 직접 초기화(비싼 ODE 초기화 우회). frozen Real Score + trainable Fake Score 모델로 MSE+DMD gradient 결합 최적화. 샘플링 timestep [0, 250, 500, 750]. (10K steps)

**(B) AR-지향 마스크 캐시(AR-Oriented Mask Cache)** — 편집 활동에 따라 연산을 동적 라우팅.
- 직전 chunk에서 L₂ 거리(‖z_edit^{k-1}−z_src^{k-1}‖₂ > τ)로 편집/정적 영역의 이진 마스크 추출.
- 약 70% 중복 토큰을 가지치기(prune). 미편집 영역은 재계산 대신 캐시된 중간 feature를 재사용.
- 캐시는 Self-Attention 층에만 적용(FFN 재사용 시 고주파 민감도로 열화 발생). 프레임당 약 79ms 지연.

## 5. 핵심 기여 (Contributions)
- 양방향 기반 모델을 효율적 4-step 인과 모델로 이전하며 어텐션 분포 이동 문제를 푸는 **3단계 증류 파이프라인**.
- 편집/정적 영역을 L₂ 거리로 동적 식별해 중복 연산을 줄이고 배경 보존을 보장하는 **AR-지향 마스크 캐시**.
- 120개 video-text 쌍으로 구성한 **스트리밍 비디오 편집 벤치마크** 제시.
- 12.66 FPS 실시간 추론으로 인터랙티브/AR 응용을 가능케 하며, 텍스트 정합·배경 일관성에서 SOTA 달성.

## 6. 실험 / 결과 — 데이터셋, 지표, 주요 수치
- **학습 데이터**: Ditto-1M에서 필터링한 고품질 video-video 쌍 20K.
- **평가 벤치마크**: 저자 수집 120개 video-instruction 쌍.
- **지표(VBench + CLIP)**: Text Alignment(TA↑), Background Consistency(BC↑), Motion Smoothness(MS↑), Dynamic Degree(DD↑), Aesthetic Quality(AQ↑), Imaging Quality(IQ↑).
- **주요 수치 (Ours W/ Cache)**: TA 0.270, BC 0.956, MS 0.992, DD 0.256, AQ 0.581, IQ 0.708 — TA·BC·MS에서 baseline(LucyEdit, InsV2V, StreamDiffusion 등) 대비 최고.
- **속도**: 12.66 FPS, 프레임당 79ms (3프레임 기준); Stage 3 적용 시 81프레임 7.89초, 4 NFE, CFG 불필요.
- **User study**: 20명, 전체 품질에서 top-3 선호율 95.8%, 지시 일관성·배경 보존·종합 품질 전반에서 우세.
- **Ablation**: Stage 1(100 NFE, CFG 필요, 비스트리밍) → Stage 2(스트리밍 추가) → Stage 3(4 NFE, CFG 불필요). 캐시 위치는 Self-Attention이 최적, FFN 캐시는 심한 열화.

## 7. 한계 및 향후 과제
- 저자는 이러한 증류 기법을 일반 비디오 편집에 직접 이전하면 고충실도(high-fidelity) 디테일이 종종 열화된다고 인정.
- 콘텐츠에 따라 마스크 임계값(τ) 동역학의 세심한 튜닝이 필요.

## 8. 구현 단서 — 공식 코드 저장소 링크(있으면), 핵심 하이퍼파라미터
- **프로젝트 페이지**: https://live-edit.github.io
- **공식 코드 저장소**: 원문에 명시 없음 (접근 가능 콘텐츠 내 GitHub 저장소 링크 미기재).
- **핵심 하이퍼파라미터**:
  - 베이스 모델: Wan2.1-T2V-1.3B / 아키텍처: Diffusion Transformer
  - 하드웨어: NVIDIA A100 8장 / 옵티마이저: AdamW (전 단계)
  - 학습: Stage1 9K + Stage2 20K + Stage3 10K = 총 39K steps, lr 1e-5, batch 8
  - 추론: 4 steps, 3-latent-frame chunk, 샘플링 timestep [0, 250, 500, 750]
  - 마스크 캐시: L₂ 임계값 τ로 약 70% 토큰 pruning, Self-Attention 층에만 적용
  - 성능: 12.66 FPS, 프레임당 79ms
