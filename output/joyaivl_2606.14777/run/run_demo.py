#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
Minimal, GPU-free reproduction demo for JoyAI-VL-Interaction (arXiv 2606.14777).

WHAT THIS PROVES (with toy inputs, no training, no GPU, no 8B weights):
  A) datasets/convert_data.py :: convert_sample()
       -> per-second (user / assistant) supervision, with SILENCE as a
          first-class label ("</silence>") and time markers "<{s}.0 seconds>".
  B) services/webui/.../vlm_service.py :: analyze_image()
       -> frame "encoding" = PIL -> JPEG -> base64 -> "data:image/jpeg;base64,..."
  C) services/webinfer/live_adapter.py :: _resize_image_if_needed(max_pixels=262144)
       -> per-frame visual-token budget cap via spatial downscale.
  D) services/webinfer/live_adapter.py :: normalize_model_output() /
       extract_response_payload() + FORCE_SILENCE_BEFORE_QUERY short-circuit
       -> the paper's per-second 3-way decision (</silence> | </response> |
          </delegation>) realized as adapter-level string normalization,
          driven here by a MOCK vLLM (so no real model weights are needed).

NOTE ON FIDELITY: the upstream repo is not checked into this workspace, so the
functions below are faithful re-implementations of the behavior documented in
04_code.md (exact constants preserved: max_pixels=262144, frame_seconds=1.0,
skip_special_tokens=False, the three decision markers). This demo therefore
proves the *documented* pipeline end-to-end on toy data; it does NOT load the
proprietary JoyAI-VL-8B checkpoint or run vLLM. Those requirements are listed
in 05_run.md.
"""

import base64
import io
import json
import re
from typing import Optional

from PIL import Image

# ----------------------------------------------------------------------------
# Constants mirrored from the repo (04_code.md).
# ----------------------------------------------------------------------------
MAX_PIXELS = 262144           # ~512x512 per-frame visual-token cap (live_adapter.py)
FRAME_SECONDS = 1.0           # 1 Hz sampling; 1 timestep = 1 second
FORCE_SILENCE_BEFORE_QUERY = True
SKIP_SPECIAL_TOKENS = False    # decision tokens must survive decoding

SILENCE = "</silence>"
RESPONSE = "</response>"
DELEGATION = "</delegation>"


def hr(title):
    print("\n" + "=" * 72)
    print(title)
    print("=" * 72)


# ----------------------------------------------------------------------------
# (A) datasets/convert_data.py :: convert_sample()  (silence as first-class label)
# ----------------------------------------------------------------------------
def convert_sample(sample, frames_dir_prefix="frames"):
    """Raw time-aligned annotation -> per-second user/assistant message pairs.

    sample = {
        "video_name", "fps", "duration",
        "question_map":  {sec:int -> question:str},
        "response_map":  {sec:int -> spoken answer:str},
    }
    """
    fps = sample.get("fps", 1)
    duration = sample.get("duration", 1)
    question_map = sample.get("question_map", {})
    response_map = sample.get("response_map", {})

    frames_per_sec = max(int(fps), 1)
    n_seconds = max(int(duration), 1)

    messages = []
    image_paths = []
    for sec in range(n_seconds):
        parts = []
        if sec in question_map:
            parts.append(question_map[sec])
        parts.append(f"<{sec:.1f} seconds>")          # timing supervision marker
        for f in range(frames_per_sec):
            parts.append("<image>")
            image_paths.append(f"{frames_dir_prefix}/{sample['video_name']}_s{sec}_f{f}.jpg")
        messages.append({"role": "user", "content": "\n".join(parts)})

        if sec in response_map:                        # content supervision
            messages.append({"role": "assistant",
                             "content": f"{RESPONSE} {response_map[sec]}"})
        else:                                          # SILENCE = first-class label
            messages.append({"role": "assistant", "content": SILENCE})

    return {
        "messages": messages,
        "images": image_paths,
        "video_name": sample["video_name"],
        "task_type": "streaming_interaction",
        "source": "demo_toy",
    }


# ----------------------------------------------------------------------------
# (C) live_adapter.py :: _resize_image_if_needed(max_pixels)
# ----------------------------------------------------------------------------
def _resize_image_if_needed(image, max_pixels=MAX_PIXELS):
    width, height = image.size
    if max_pixels <= 0 or width * height <= max_pixels:
        return None
    scale = (max_pixels / (width * height)) ** 0.5
    new_size = (int(width * scale), int(height * scale))
    return image.resize(new_size, Image.LANCZOS)


# ----------------------------------------------------------------------------
# (B) vlm_service.py :: analyze_image()  (frame -> JPEG -> base64 data URL)
# ----------------------------------------------------------------------------
def encode_frame_as_data_url(image, max_pixels=MAX_PIXELS):
    resized = _resize_image_if_needed(image, max_pixels)
    if resized is not None:
        image = resized
    buf = io.BytesIO()
    image.save(buf, format="JPEG")                     # full JPEG per frame (no P-token)
    b64 = base64.b64encode(buf.getvalue()).decode("utf-8")
    return f"data:image/jpeg;base64,{b64}", image.size, len(b64)


def build_openai_content(text, image, frame_time_range, max_pixels=MAX_PIXELS):
    url, size, b64len = encode_frame_as_data_url(image, max_pixels)
    content = [
        {"type": "text", "text": f"{text}\n{frame_time_range}"},
        {"type": "image_url", "image_url": {"url": url}},
    ]
    return content, size, b64len


# ----------------------------------------------------------------------------
# (D) live_adapter.py :: decision-token normalization
# ----------------------------------------------------------------------------
def normalize_model_output(text: str) -> str:
    """Reduce free-form model text to exactly one standard decision form."""
    if text is None:
        return SILENCE
    t = text.strip()
    # find earliest decision marker
    markers = [(t.find(SILENCE), SILENCE),
               (t.find(RESPONSE), RESPONSE)]
    present = [(pos, m) for pos, m in markers if pos != -1]
    if not present:
        first_line = t.splitlines()[0].strip() if t else ""
        return f"{RESPONSE} {first_line}" if first_line else SILENCE
    present.sort(key=lambda x: x[0])
    _, marker = present[0]
    if marker == SILENCE:
        return SILENCE
    payload = t.split(RESPONSE, 1)[1].strip()
    return f"{RESPONSE} {payload}" if payload else SILENCE


def extract_response_payload(normalized: str) -> Optional[str]:
    if normalized.startswith(SILENCE):
        return None
    return normalized.split(RESPONSE, 1)[1].strip()


def parse_delegation(raw: str):
    """Detect background delegation from the 2-stage </response> ... </delegation> form."""
    if DELEGATION in raw:
        q = raw.split(DELEGATION, 1)[1].strip()
        m = re.search(r"<question>(.*?)</question>", q, re.S)
        return (m.group(1).strip() if m else q) or None
    return None


# ----------------------------------------------------------------------------
# MOCK vLLM main model. Returns raw text containing decision tokens, so we can
# exercise the whole adapter path with NO real weights / GPU.
# ----------------------------------------------------------------------------
def mock_main_model(user_query: Optional[str], scripted_reply: str) -> str:
    # skip_special_tokens=False is what the real adapter forces on vLLM so that
    # </silence> etc. are not stripped. Here the mock simply returns them verbatim.
    return scripted_reply


def adapter_decide(user_query, scripted_reply):
    """One per-second decision step of live_adapter._handle_chat_payload()."""
    # (2) FORCE_SILENCE_BEFORE_QUERY: no query -> return </silence>, skip model.
    if FORCE_SILENCE_BEFORE_QUERY and not user_query:
        return {"decision": SILENCE, "payload": None,
                "delegation": None, "called_model": False}
    raw = mock_main_model(user_query, scripted_reply)          # (5) call main model
    norm = normalize_model_output(raw)                         # (6) normalize
    payload = extract_response_payload(norm)
    delegation = parse_delegation(raw)
    decision = SILENCE if norm == SILENCE else (
        "delegation" if delegation else RESPONSE)
    return {"decision": decision, "payload": payload,
            "delegation": delegation, "called_model": True}


# ----------------------------------------------------------------------------
# Toy frame generator (no external assets needed).
# ----------------------------------------------------------------------------
def make_toy_frame(sec, w=640, h=480):
    # deterministic gradient so JPEG size is non-trivial
    img = Image.new("RGB", (w, h))
    px = img.load()
    for y in range(h):
        for x in range(0, w, 8):      # step to keep it fast; JPEG still valid
            val = (x + y + sec * 40) % 256
            for dx in range(8):
                if x + dx < w:
                    px[x + dx, y] = (val, (val * 2) % 256, (sec * 30) % 256)
    return img


def main():
    hr("PART A - convert_sample(): per-second supervision, silence as 1st-class label")
    toy_sample = {
        "video_name": "toy_clip",
        "fps": 1,
        "duration": 5,
        # user asks a question at second 1; ground-truth speech only at sec 1 & 3
        "question_map": {1: "What is the person doing?"},
        "response_map": {1: "They are picking up a red cup.",
                         3: "Now they are pouring water."},
    }
    converted = convert_sample(toy_sample)
    print(json.dumps(converted, indent=2, ensure_ascii=False))
    n_sil = sum(1 for m in converted["messages"]
                if m["role"] == "assistant" and m["content"] == SILENCE)
    n_resp = sum(1 for m in converted["messages"]
                 if m["role"] == "assistant" and m["content"].startswith(RESPONSE))
    print(f"\n[check] assistant turns: {n_sil} silence + {n_resp} response "
          f"= {n_sil + n_resp} (expected 5 seconds -> 3 silence + 2 response)")
    assert n_sil == 3 and n_resp == 2, "silence/response label counts wrong"
    assert any("<1.0 seconds>" in m["content"] for m in converted["messages"]), "time marker missing"
    print("[ok] time markers present, silence is an explicit assistant label.")

    hr("PART B+C - frame encoding (JPEG->base64) + max_pixels resize cap")
    big = make_toy_frame(0, w=1920, h=1080)      # 2,073,600 px > 262144 -> must shrink
    small = make_toy_frame(0, w=320, h=240)      # 76,800 px <= 262144 -> untouched
    url_big, size_big, b64_big = encode_frame_as_data_url(big)
    url_small, size_small, b64_small = encode_frame_as_data_url(small)
    print(f"big frame  1920x1080 ({1920*1080} px) -> encoded at {size_big} "
          f"({size_big[0]*size_big[1]} px), data_url prefix={url_big[:30]!r}, b64_len={b64_big}")
    print(f"small frame 320x240 ({320*240} px) -> encoded at {size_small} (unchanged), b64_len={b64_small}")
    assert size_big[0] * size_big[1] <= MAX_PIXELS, "resize did not cap pixels"
    assert size_small == (320, 240), "small frame should be untouched"
    assert url_big.startswith("data:image/jpeg;base64,"), "bad data url"
    print(f"[ok] big frame capped to <= {MAX_PIXELS} px; small frame passed through; "
          f"both emitted as data:image/jpeg;base64 URLs.")

    hr("PART D - per-second decision loop (MOCK vLLM): silence / response / delegation")
    # Scripted mock replies per second (what the 8B model *would* emit).
    timeline = [
        # (second, user_query, scripted_raw_model_reply)
        (0, None,                       "(model not called)"),
        (1, "What is the person doing?", "</response> They are picking up a red cup."),
        (2, None,                       "(model not called)"),
        (3, "Is the cup full now?",     "</silence>"),   # model chooses to stay silent
        (4, "Summarize a 2h security recording and email me.",
            "</response> On it. </delegation> <question>Summarize the 2h recording "
            "and draft an email</question>"),
    ]
    header = f"{'sec':>3} | {'query?':^6} | {'called':^6} | {'decision':^12} | payload/delegation"
    print(header)
    print("-" * len(header))
    results = []
    for sec, q, reply in timeline:
        img = make_toy_frame(sec)
        content, _, _ = build_openai_content(
            q or "(observing)", img, f"<{sec * FRAME_SECONDS:.1f} seconds>")
        r = adapter_decide(q, reply)
        results.append(r)
        extra = ""
        if r["decision"] == RESPONSE:
            extra = f"say: {r['payload']!r}"
        elif r["decision"] == "delegation":
            extra = f"delegate: {r['delegation']!r}"
        print(f"{sec:>3} | {('yes' if q else 'no'):^6} | "
              f"{('yes' if r['called_model'] else 'no'):^6} | "
              f"{r['decision']:^12} | {extra}")

    # assertions on the decision loop
    assert results[0]["decision"] == SILENCE and results[0]["called_model"] is False, \
        "sec0: no query must short-circuit to silence without calling model"
    assert results[1]["decision"] == RESPONSE and results[1]["payload"], "sec1: expected response"
    assert results[3]["decision"] == SILENCE, "sec3: model chose silence"
    assert results[4]["decision"] == "delegation" and results[4]["delegation"], "sec4: expected delegation"
    print("\n[ok] FORCE_SILENCE_BEFORE_QUERY short-circuits silent seconds without "
          "calling the model;")
    print("[ok] response / silence / delegation all parsed from raw decision tokens.")

    hr("DEMO COMPLETE - all assertions passed")
    print("Proved (toy inputs, no GPU, no 8B weights): per-second silence-first "
          "supervision, JPEG/base64 frame encoding, max_pixels resize cap, and the "
          "3-way decision-token adapter loop.")


if __name__ == "__main__":
    main()
