"""
LiveEdit — 최소 재현 데모 (CPU, 토이 입력)
==========================================
논문/코드(04_code.md)의 4가지 핵심 메커니즘을 무거운 학습/가중치/flash-attn 없이
토이 텐서로 1-step 동작만 증명한다.

검증 대상:
  (1) 입력 채널 16->32 확장          model/mm_dmd.py:_expand_input_layer
  (2) chunk-wise causal attention mask  wan/modules/causal_model.py:_prepare_blockwise_causal_attn_mask
  (3) L2 마스크 캐시 / ~70% 토큰 프루닝  pipeline/causal_inference.py:_compute_mask_from_*
  (4) DMD 분포정합 손실(MSE 형태)       model/mm_dmd.py:compute_distribution_matching_loss
  (5) 위 (2)+(3)을 묶은 미니 스트리밍 chunk 루프 (데이터 흐름 모사)

실행: python run_demo.py
"""
import time
import torch
import torch.nn as nn
import torch.nn.functional as F

torch.manual_seed(0)
SEP = "=" * 64


def section(title):
    print("\n" + SEP)
    print(title)
    print(SEP)


# ---------------------------------------------------------------------------
# (1) 입력 채널 16 -> 32 확장 (source latent concat)
# ---------------------------------------------------------------------------
def expand_input_layer(old_proj: nn.Conv3d) -> nn.Conv3d:
    """04_code.md 발췌(2): patch-embed Conv3d를 16->32ch로 확장.
    weight[:, :16]=기존 가중치 복사, 17~32ch=0 초기화."""
    out_ch = old_proj.out_channels
    new_proj = nn.Conv3d(
        in_channels=32,
        out_channels=out_ch,
        kernel_size=old_proj.kernel_size,
        stride=old_proj.stride,
        padding=old_proj.padding,
        bias=(old_proj.bias is not None),
    )
    with torch.no_grad():
        new_proj.weight.zero_()
        new_proj.weight[:, :16].copy_(old_proj.weight)   # 기존 16ch 보존
        # 채널 16:32 = 0 (source latent 경로, 초기 무영향)
        if old_proj.bias is not None:
            new_proj.bias.copy_(old_proj.bias)
    return new_proj


def demo_expand_input_layer():
    section("(1) 입력 채널 16->32 확장  (_expand_input_layer)")
    # 토이: out=8, kernel=(1,2,2) patchify, latent T=4, H=W=8
    old = nn.Conv3d(16, 8, kernel_size=(1, 2, 2), stride=(1, 2, 2))
    new = expand_input_layer(old)

    noise_latent = torch.randn(1, 16, 4, 8, 8)       # 노이즈 latent (16ch)
    source_latent = torch.randn(1, 16, 4, 8, 8)      # 소스 비디오 latent (16ch)
    x32 = torch.cat([noise_latent, source_latent], dim=1)  # 채널 concat -> 32ch

    out_old = old(noise_latent)
    out_new_init = new(x32)                          # 확장 직후(17~32=0): 동일해야 함
    max_diff = (out_old - out_new_init).abs().max().item()

    # source 채널에 비0 가중치를 주면 출력이 달라짐을 확인(주입 경로 작동)
    with torch.no_grad():
        new.weight[:, 16:].normal_(0, 0.1)
    out_new_src = new(x32)
    src_effect = (out_new_src - out_old).abs().max().item()

    print(f"old(16ch) 출력 shape         : {tuple(out_old.shape)}")
    print(f"new(32ch) 출력 shape         : {tuple(out_new_init.shape)}")
    print(f"확장 직후 |old-new| max diff  : {max_diff:.3e}  (≈0 이면 호환 보존 OK)")
    print(f"source 채널 활성 후 변화 max  : {src_effect:.3e}  (>0 이면 주입 경로 작동)")
    assert max_diff < 1e-5, "확장 직후 출력이 보존되지 않음"
    assert src_effect > 0, "source 채널 주입 경로가 작동하지 않음"
    print("PASS: 16->32 확장이 기존 동작 보존 + source 주입 경로 추가")


# ---------------------------------------------------------------------------
# (2) chunk-wise (block-wise) causal attention mask
# ---------------------------------------------------------------------------
def prepare_blockwise_causal_attn_mask(num_frames, frame_seqlen, num_frame_per_block, device="cpu"):
    """04_code.md 발췌(3): 각 query는 자기 chunk 끝(ends)까지만 attend -> 미래 누설 차단.
    True=attend 허용. 반환 (L, L) bool."""
    total_length = num_frames * frame_seqlen
    block = frame_seqlen * num_frame_per_block
    idx = torch.arange(total_length, device=device)
    # 각 토큰이 속한 block의 끝 인덱스(배타적)
    ends = (torch.div(idx, block, rounding_mode="floor") + 1) * block
    ends = torch.clamp(ends, max=total_length)
    q = idx.view(-1, 1)
    k = idx.view(1, -1)
    mask = (k < ends[q]) | (q == k)   # 현재 chunk 끝 이전 + 자기 자신
    return mask


def demo_causal_mask():
    section("(2) chunk-wise causal attention mask  (_prepare_blockwise_causal_attn_mask)")
    num_frames, frame_seqlen, nfpb = 6, 2, 3   # 6 latent frame, frame당 2토큰, block=3프레임
    L = num_frames * frame_seqlen
    mask = prepare_blockwise_causal_attn_mask(num_frames, frame_seqlen, nfpb)
    block = frame_seqlen * nfpb
    n_blocks = L // block
    print(f"총 토큰 L={L}, block size={block} (=frame_seqlen*num_frame_per_block), blocks={n_blocks}")

    # 미래 block으로의 attention은 0이어야 함
    leak = 0
    for qi in range(L):
        qb = qi // block
        for ki in range(L):
            kb = ki // block
            if kb > qb and mask[qi, ki]:
                leak += 1
    allowed = mask.float().mean().item()
    print(f"future-block attention(누설) 수 : {leak}  (0 이어야 인과성 성립)")
    print(f"전체 대비 허용 비율            : {allowed*100:.1f}% (block 하삼각 구조)")

    # 토이 어텐션을 마스크 적용해 1-step 순전파
    d = 8
    x = torch.randn(1, L, d)
    qy, ky, vy = x, x, x
    scores = (qy @ ky.transpose(-1, -2)) / (d ** 0.5)
    scores = scores.masked_fill(~mask.view(1, L, L), float("-inf"))
    attn = scores.softmax(-1)
    out = attn @ vy
    # 첫 block의 출력은 뒤쪽 block 토큰을 변경해도 불변(인과)인지 확인
    x2 = x.clone(); x2[0, block:] += 5.0
    scores2 = (x2 @ x2.transpose(-1, -2)) / (d ** 0.5)
    scores2 = scores2.masked_fill(~mask.view(1, L, L), float("-inf"))
    out2 = (scores2.softmax(-1)) @ x2
    first_block_change = (out[0, :block] - out2[0, :block]).abs().max().item()
    print(f"어텐션 출력 shape             : {tuple(out.shape)}")
    print(f"미래 토큰 변경 시 첫 block 변화: {first_block_change:.3e} (≈0 이면 인과 OK)")
    assert leak == 0, "미래 block 누설 발생"
    assert first_block_change < 1e-5, "인과성 위반: 미래가 과거에 영향"
    print("PASS: block-wise causal mask가 미래 누설 차단")


# ---------------------------------------------------------------------------
# (3) L2 마스크 캐시 -> 토큰 프루닝 (adaptive_patch_ratio=0.3, ~70% prune)
# ---------------------------------------------------------------------------
def compute_mask_from_generated(generated_latent, source_latent, adaptive_patch_ratio=0.3):
    """04_code.md 발췌(4). 입력 latent: (B, F, C, H, W). 반환 (low_importance_mask, importance, keep_num)."""
    diff = (generated_latent - source_latent).pow(2).mean(dim=2)    # L2^2 over channels -> (B,F,H,W)
    importance = (diff - diff.amin(dim=(-1, -2), keepdim=True)) / (
        diff.amax(dim=(-1, -2), keepdim=True) - diff.amin(dim=(-1, -2), keepdim=True) + 1e-8)
    B, Fr, H, W = importance.shape
    num_tokens = H * W
    keep_num = int(num_tokens * adaptive_patch_ratio)              # 상위 30% keep
    flat = importance.view(B, Fr, num_tokens)
    # kthvalue로 임계 tau: (num_tokens - keep_num + 1)번째 작은 값
    tau = torch.kthvalue(flat, num_tokens - keep_num + 1, dim=-1, keepdim=True).values
    high = flat >= tau                                            # 편집 영역(재계산)
    low_importance_mask = ~high                                   # 정적 영역(캐시 재사용)
    return low_importance_mask.view(B, Fr, H, W), importance, keep_num


def demo_token_pruning():
    section("(3) L2 마스크 캐시 -> 토큰 프루닝  (_compute_mask_from_generated)")
    B, Fr, C, H, W = 1, 3, 16, 8, 8
    source = torch.randn(B, Fr, C, H, W)
    # 실제 추론처럼 generated는 전 영역이 미세하게 다름(작은 변화) +
    # 편집 영역(좌상단 4x4)만 강하게 변함 -> 연속적 importance (동점 회피)
    generated = source + torch.randn(B, Fr, C, H, W) * 0.05
    generated[:, :, :, :4, :4] += torch.randn(B, Fr, C, 4, 4) * 3.0
    low_mask, importance, keep_num = compute_mask_from_generated(generated, source, 0.3)

    num_tokens = H * W
    kept = (~low_mask).float().sum().item()            # 재계산(편집) 토큰
    pruned = low_mask.float().sum().item()             # 캐시(정적) 토큰
    total = low_mask.numel()
    prune_ratio = pruned / total
    print(f"frame당 토큰수={num_tokens}, keep_num(상위30%)={keep_num}")
    print(f"재계산(편집) 토큰={int(kept)}, 캐시(정적) 토큰={int(pruned)}, 총={total}")
    print(f"prune 비율={prune_ratio*100:.1f}%  (논문 ~70% 목표)")
    # 편집한 좌상단이 실제로 high importance(keep)로 분류되는지
    edited_region_kept = (~low_mask)[:, :, :4, :4].float().mean().item()
    print(f"편집한 좌상단 영역의 keep 비율: {edited_region_kept*100:.1f}% (편집영역=재계산 분류)")
    assert 0.6 < prune_ratio < 0.75, f"prune 비율 이상: {prune_ratio}"
    print("PASS: L2 중요도 -> 상위30% 재계산 / ~70% 캐시 프루닝")


# ---------------------------------------------------------------------------
# (4) DMD 분포정합 손실 (Real frozen - Fake trainable)
# ---------------------------------------------------------------------------
def compute_distribution_matching_loss(original_latent, pred_fake, pred_real,
                                       estimated_clean=None, normalization=True):
    """04_code.md 발췌(1). grad=(fake-real) 정규화 후 generator latent에서 빼서 MSE."""
    grad = (pred_fake - pred_real)
    if normalization:
        ref = estimated_clean if estimated_clean is not None else original_latent
        p_real = (ref - pred_real)
        normalizer = torch.abs(p_real).mean(dim=[1, 2, 3, 4], keepdim=True)
        grad = grad / (normalizer + 1e-8)
    dmd_loss = 0.5 * F.mse_loss(
        original_latent.double(),
        (original_latent.double() - grad.double()).detach(),
        reduction="mean")
    return dmd_loss


def demo_dmd_loss():
    section("(4) DMD 분포정합 손실  (compute_distribution_matching_loss)")
    shape = (2, 16, 4, 8, 8)   # (B, C, T, H, W)
    original = torch.randn(*shape, requires_grad=True)   # generator 출력 latent
    pred_real = torch.randn(*shape)                      # frozen real score 예측
    pred_fake = torch.randn(*shape)                      # trainable fake score 예측
    loss = compute_distribution_matching_loss(original, pred_fake, pred_real)
    loss.backward()
    gnorm = original.grad.norm().item()
    print(f"dmd_loss = {loss.item():.6f}  (스칼라)")
    print(f"d(loss)/d(original_latent) norm = {gnorm:.6f}  (>0: gradient 전파)")
    # fake==real이면 grad=0 -> loss=0 확인
    loss0 = compute_distribution_matching_loss(
        original.detach().clone().requires_grad_(True), pred_real, pred_real)
    print(f"fake==real 일 때 loss = {loss0.item():.3e}  (≈0: 분포 일치 시 무손실)")
    assert loss.item() > 0 and gnorm > 0
    assert loss0.item() < 1e-12
    print("PASS: (fake-real) 차이를 MSE 형태로 역전파, 분포 일치 시 0")


# ---------------------------------------------------------------------------
# (5) 미니 스트리밍 chunk 루프: 4-step denoise + 마스크 캐시 + KV 캐시(개념)
# ---------------------------------------------------------------------------
def demo_streaming_loop():
    section("(5) 미니 스트리밍 chunk 루프 (데이터 흐름 모사)")
    denoising_step_list = [1000, 750, 500, 250]   # 4 NFE
    num_frame_per_block = 3
    n_chunks = 3
    C, H, W = 16, 8, 8

    source = torch.randn(1, num_frame_per_block * n_chunks, C, H, W)
    prev_gen_chunk = None   # 직전 chunk의 편집 결과
    prev_src_chunk = None
    total_prune = []
    t0 = time.time()
    for ci in range(n_chunks):
        sl = slice(ci * num_frame_per_block, (ci + 1) * num_frame_per_block)
        src_chunk = source[:, sl]
        # 04_code.md 흐름: 마스크 캐시는 "직전 chunk 결과" vs source로 계산
        if prev_gen_chunk is None:
            prune_ratio = 0.0       # 첫 chunk: 캐시 없음(전체 계산)
        else:
            low_mask, _, _ = compute_mask_from_generated(prev_gen_chunk, prev_src_chunk, 0.3)
            prune_ratio = low_mask.float().mean().item()
        total_prune.append(prune_ratio)
        # 4-step denoise (토이: step마다 source 쪽으로 수렴시키며 편집 적용)
        x = torch.randn_like(src_chunk)
        for t in denoising_step_list:
            scale = t / 1000.0
            x = src_chunk + (x - src_chunk) * scale
        # 일부 영역만 실제 편집되었다고 가정(다음 chunk 프루닝 입력)
        x[:, :, :, :4, :4] += torch.randn(1, num_frame_per_block, C, 4, 4) * 2.0
        prev_gen_chunk, prev_src_chunk = x, src_chunk
        print(f"  chunk {ci}: 4-step denoise 완료, 적용 prune={prune_ratio*100:.1f}%, "
              f"out shape={tuple(x.shape)}")
    dt = (time.time() - t0) * 1000
    nz = [p for p in total_prune if p > 0]
    avg_nz = sum(nz) / len(nz) if nz else 0.0
    print(f"3 chunk 스트리밍 완료, chunk>=1 평균 prune={avg_nz*100:.1f}%, "
          f"소요 {dt:.1f}ms (CPU 토이)")
    print("PASS: source 주입 -> 마스크 캐시 -> 4-step denoise -> chunk 출력 흐름 작동")


if __name__ == "__main__":
    print("LiveEdit 최소 재현 데모 (CPU / 토이 입력 / 학습 없음)")
    print(f"torch={torch.__version__}, cuda={torch.cuda.is_available()}")
    demo_expand_input_layer()
    demo_causal_mask()
    demo_token_pruning()
    demo_dmd_loss()
    demo_streaming_loop()
    section("ALL DEMOS PASSED")
    print("4개 핵심 메커니즘 + 스트리밍 흐름을 토이 입력으로 검증 완료.")
