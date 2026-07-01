# output/ — 논문별 분석 산출물

각 논문은 **자체 폴더**(`output/<slug>/`)에 정리됩니다. `paper-os`가 Triage 단계에서
논문마다 파일시스템 안전 slug를 지어 폴더를 만들고, 모든 단계 산출물을 그 안에 저장합니다.

## 폴더 구조 (논문 1편 기준)
```
output/<slug>/
├─ 01_analysis.md      # 논문 분석 (/analyzer)
├─ 03_detail.md        # 상세 해설 (/detail)  ← part 파일은 병합 후 삭제됨
├─ 04_code.md          # 구현 코드 분석 (/code)
├─ 05_run.md           # 실행 리포트 (/code-run)
├─ design.css          # 디자인 시스템 (/mydesign)
├─ report.html         # 최종 HTML 리포트 (/html) ← 결과물
├─ feedback_summary.md # 단계별 게이트 종합 (/feedback)
└─ run/                # 실행 가능한 최소 재현 코드
   ├─ requirements.txt
   └─ run_demo.py
```

## 분석된 논문
| slug | 논문 | 최종 리포트 |
|---|---|---|
| `liveedit_2606.26740` | LiveEdit — Towards Real-Time Diffusion-Based Streaming Video Editing | [report.html](liveedit_2606.26740/report.html) |
| `joyaivl_2606.14777` | JoyAI-VL-Interaction — Real-Time Vision-Language Interaction Intelligence | [report.html](joyaivl_2606.14777/report.html) |

> 새 논문을 분석하면 이 표에 한 줄씩 추가하세요.
