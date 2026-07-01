# 피드백 종합 (JoyAI-VL-Interaction) — 전 단계 게이트 결과


===================================================================
# 피드백: analysis

- **판정**: PASS ✅  (점수: 7/10)

## 항목별 평가
- [x] **8개 섹션 모두 존재** — 1.요약 ~ 8.구현 단서까지 8개 섹션이 빠짐없이 구성됨.
- [x] **수치 근거 충실** — 승/무/패 비율(vs Doubao 77.6/17.2/5.2, vs Gemini 87.9/10.3/1.7), 시나리오별 승률, 손실 가중치($w_{silence}^{first}=1$, $=0.4$, $w_{response}=1.5$), 메모리 파라미터($T_s=100$, $M=5$, $L=15$), 1Hz, ~16토큰/프레임 등 핵심 수치가 구체적이고 내부 정합적임.
- [x] **핵심 링크 유효** — GitHub(github.com/jd-opensource/JoyAI-VL-Interaction, 911★ 실재), arXiv(2606.14777, 2026-06-10 제출, 제목 일치) 모두 실재 확인됨.
- [x] **환각 없음** — 나열된 방법/기여/한계 항목이 논문 초록·저장소 설명과 일관됨.
- [ ] **저자 메타데이터 부정확** — 논문의 **제1저자 "Dingyu Yao"가 누락**됨. arXiv 실제 순서는 `Dingyu Yao, Junhao Zhou, Chenxu Yang, ...`인데 분석은 Junhao Zhou부터 시작하여 리드 저자를 빠뜨림.
- [ ] **라이선스 표기 혼동 가능** — 8절이 "전체 시스템 코드 … 라이선스 CC BY 4.0"으로 기술. 논문(arXiv) 자체 라이선스는 CC BY 4.0이 맞으나, **코드 저장소(GitHub) 라이선스는 Apache 2.0**. 코드/가중치 릴리스 문맥에 CC BY 4.0을 붙인 것은 오해 소지 있음.

## 반드시 고칠 것 (Actionable)
1. **4행 저자 목록 앞에 제1저자 `Dingyu Yao`를 추가**하여 arXiv 순서(Dingyu Yao → Junhao Zhou → …)와 일치시킬 것. 현재는 리드 저자가 빠져 인용/귀속이 틀림.
2. **8절 라이선스 표기 분리**: 논문 라이선스(CC BY 4.0)와 코드 저장소 라이선스(Apache 2.0)를 구분해 명시할 것. 예: "논문 CC BY 4.0 / 코드·가중치 Apache 2.0(GitHub 기준)".

## 권장 개선 (선택)
- 6절 릴리스 날짜 "2026년 6월 20일"과 arXiv 제출일(6월 10일)의 근거 출처를 각주로 달아 검증 가능성을 높일 것.
- 3절 비교 대상(GPT-Realtime-2, Qwen3.5-Omni 등) 제품명은 논문 표기와 철자를 대조해 두면 안전함.

---
**판정: PASS ✅ (7/10) — 저장 경로: `./output/joyaivl_2606.14777/feedback_analysis.md`**


===================================================================
# 피드백: detail

- **판정**: PASS ✅  (점수: 8/10)

대상: `./output/joyaivl_2606.14777/03_detail.md`
기준: `./.claude/skills/feedback/SKILL.md` detail 체크리스트 (직관/비유/단계/예시 4단 구조 · 용어 풀이 · 난이도 적정)

## 항목별 평가

- [x] **4단 구조(직관 → 비유 → 단계별 전개 → 예시)** — 개념 1·2는 정확히 4단 구조를 따름. 개념 3은 "왜 필요한가" 도입 후 하위개념 A(계층 기억)/B(위임) 각각에 직관·비유·단계별·예시를 배치해 구조 유지. 각 개념에 "전체 흐름 한눈에 보기(ASCII 다이어그램)" + FAQ까지 더해 이해 보강.
- [x] **용어 풀이** — 별도 "공통 사전 지식" 섹션에서 VLM·턴기반·반응형/능동형·스트리밍·1Hz·토큰·CE 손실·가중치·시간정렬·SFT·동기/비동기·컨텍스트 윈도우를 한 줄씩 풀이. 본문에서도 WebRTC/RTSP, prefix caching 등을 인라인 각주식으로 설명. 충실함.
- [x] **난이도 적정** — 비전공 독자도 따라올 수 있는 눈높이. 비유(축구 중계 감독·응급실 간호사·식당 홀 매니저)와 토이 시나리오(낙상·보안카메라·가게 CCTV·영상통화)로 추상 개념을 구체화. 수식은 직관 표기임을 명시.
- [x] **출처 정합성** — 가중치(1/0.4/1.5), 메모리 하이퍼파라미터(T_s=100·M=5·L=15·약 2시간), 6개 데이터 계열, 벤치마크 승률(Doubao 77.6% / Gemini 87.9%), 낙상 사례(Doubao 4~5초 지연·Gemini 미감지) 모두 `01_analysis.md`와 일치. 창작 예시/비유는 원문 비출처임을 말미에 명시.
- [ ] **"4M+" 단위 해석 오류 가능성** — `01_analysis.md`는 "4M+ **시간 정렬**(time-aligned) 스트리밍 클립"으로, '시간 정렬'은 time-aligned(초 단위 라벨 정렬)라는 **수식어**이고 4M+는 클립/샘플 **개수**로 읽는 것이 자연스러움. 그러나 detail 문서는 이를 "400만 **시간(hours)** 이상"(총 영상 길이)으로 1차 해석함(line 232, FAQ Q4 line 339~340). "정확한 단위는 원논문 확인 권장"이라 헷지는 했으나, 근거 문서와 상충하는 해석을 주 서술로 제시.
- [ ] **TOC 앵커 접미사 정합성(경미)** — "단계별 전개" 제목이 문서 내 다수 존재하여 GitHub 자동 앵커가 `단계별-전개`, `-1`, `-2`… 로 부여됨. TOC의 개념1 링크(`#단계별-전개-1`)가 첫 번째 헤딩(접미사 없는 `#단계별-전개`)과 어긋날 수 있음. 렌더러에 따라 점프 실패 가능.

## 반드시 고칠 것 (Actionable)

1. **"4M+" 표현을 근거 문서에 맞게 재서술** — line 232의 "400만(4M) 시간 이상의 시간정렬 스트리밍 클립"과 FAQ Q4(line 339~340)를, "4M+는 (시간정렬된) 스트리밍 **클립/샘플 약 400만 개**로 보는 것이 자연스러우며, '시간정렬'은 time-aligned를 뜻함. 단, 원문 축약 표기라 총량 단위는 원논문/저장소 확인 권장"으로 수정. 현재처럼 'hours(총 영상 길이)'를 주 해석으로 두지 말 것. (`01_analysis.md` line 31 대조)

## 권장 개선 (선택)

- TOC의 "단계별 전개" 앵커 접미사를 실제 렌더링 앵커와 맞추거나, 개념별로 제목을 "단계별 전개 — 개념 1"처럼 고유화해 링크 깨짐을 방지.
- FAQ Q5(개념1)의 "77.6% 승 / 87.9% 승"은 사례가 아닌 human-rater 종합 결과이므로, 오해 방지를 위해 "58개 케이스 human 평가 기준" 한 구절을 덧붙이면 정밀도가 올라감.

---
**판정: PASS ✅ (8/10)** · 저장: `./output/joyaivl_2606.14777/feedback_detail.md`


===================================================================
# 피드백: code

- **판정**: PASS ✅  (점수: 9/10)

## 항목별 평가

- [x] **저장소 링크 유효** — `https://github.com/jd-opensource/JoyAI-VL-Interaction` 실재 확인(웹 확인). 공식 `jd-opensource` 조직, "8B-scale fully open vision-language interaction model with a complete deployable system" 소개와 일치. 언어 구성(Python/HTML/Shell)도 서빙+WebUI 스택 서술과 부합.
- [x] **논문↔코드 매핑 4행 이상** — 통합 매핑표 18행 + Part별 세부 매핑(P0/P1/P2)까지 포함. 요구치(4행) 대폭 초과.
- [x] **실행 단서 구체적** — 진입점(`services/scripts/run.sh minimal|all`, `start_model.sh`, `live_adapter.py`, `convert_data.py`), 포트 맵(7060/8065/8070/8099/8079), 최소 재현 절차(모델 다운로드→서빙→어댑터→요청)까지 파일:함수/상수 단위로 구체적.
- [x] **정직성/환각 억제** — AdaCodec이 릴리스에 미구현(README TODO 로드맵 항목)이라는 핵심 발견을 근거(README.md:188/96)와 함께 명시. 웹 확인 결과 README에 "🚧 Limitations and Future Work" / "📋 TODO" 섹션 및 AdaCodec을 predictive video codec으로 언급하는 서술이 실재해 이 판단과 정합. 미확인/추정 항목은 **추정** 태그로 일관 표기.
- [x] **데이터 흐름 추적** — 입력→인코딩→베이스모델→출력 경로를 Part별 ASCII 다이어그램으로 제시, 코드 발췌와 연결.

## 반드시 고칠 것 (Actionable)
- 없음(필수 항목 전부 충족).

## 권장 개선 (선택)
1. **커밋 해시 일관성**: 본문 상단은 clone 커밋을 `9d07596`로 명시하나 말미 정합성 노트는 "`main` 브랜치"만 언급. 두 표현을 한 곳에서 고정(예: "main @ 9d07596")하면 재현 기준이 명확해짐.
2. **추정 값의 확정 시도**: `CHUNK`(100 vs 200), `ASYNC_SUMMARY_LEAD_FRAMES`(10 vs 20), 메모리 상수 T_s/M/L 등 코드 dataclass↔스크립트 기본값 불일치를 "설정 로딩 시점 확인 필요 — 추정"으로 남겼는데, `live_adapter.py`의 `AdapterConfig` dataclass 실제 기본값을 한 번 더 인용해 확정하면 표의 신뢰도가 상승.
3. **`</delegate>` 실시간 라우팅**: Part1은 "어댑터 능동 파싱 미확인(추정: background-agent 담당)", Part2는 `background_model.py:parse_delegation()`이 감지한다고 서술 — 두 파트의 위임 파싱 주체 서술을 교차 정리하면 미세한 상충 인상을 제거 가능.

---
**판정: PASS ✅ (9/10) — 저장 경로: C:/Users/wagra/claude/code/output/joyaivl_2606.14777/feedback_code.md**


===================================================================
# 피드백: run

- **판정**: PASS ✅  (점수: 8/10)

## 항목별 평가

- [x] **재현 명령 존재** — PowerShell 한 블록(28~37행)에 인터프리터 지정(`$PY`), `PYTHONIOENCODING`, `cd`, `pip install -r requirements.txt`, `python run_demo.py`까지 복붙 가능하게 제공. 시스템 python이 Store 스텁(9009)이라는 함정도 명시.
- [x] **실제 실행 로그 있음(검증 완료)** — 검증자가 `C:\Users\wagra\anaconda3\python.exe run_demo.py`를 직접 실행해 EXIT=0, 전 assertion 통과 확인. Part A(3 silence+2 response), Part B+C(1920x1080→682x384=261888px, small 320x240 무변경, b64_len=88628/7472), Part D 결정표(sec0 silence/미호출, sec1 response, sec3 silence, sec4 delegation) 모두 리포트 로그와 **정확히 일치**해 로그가 조작이 아님을 재현으로 입증.
- [x] **결과 해석 타당** — 각 파트를 04_code.md 표(#3/#6/#7/#9/#11/#14)·Part0/Part1 절과 교차 매핑하고, "silence=일급 라벨", "max_pixels 다운스케일=AdaCodec 대체", "JPEG/base64=P-토큰 없음", "FORCE_SILENCE_BEFORE_QUERY=모델 미호출" 등 논문 메커니즘과의 대응이 코드 동작과 부합.
- [x] **정직성/범위 한정 우수** — 못 돌린 것(8B vLLM 서빙, AdaCodec 미구현, 가중 CE/GRPO 외부 프레임워크, WebRTC 루프)을 명시하고, 데모가 업스트림 원본이 아니라 04_code.md 문서화 동작의 "충실한 재구현"임을 상수 보존(262144/1.0/skip_special_tokens=False/3마커)과 함께 투명하게 고지.
- [ ] **로그 발췌의 마지막 줄이 프로그램 실제 출력과 불일치** — 70행 `DEMO COMPLETE - all assertions passed   (EXIT=0, ELAPSED_SEC=1.12)`는 "실제 실행 로그(발췌)" 코드블록 안에 있으나, `run_demo.py`는 해당 위치에서 제목 줄과 `Proved (...)` 문장만 출력하고 `(EXIT=0, ELAPSED_SEC=1.12)` 주석은 프린트하지 않음. 실측 외부 값을 프로그램 출력인 것처럼 섞어 넣어 "실제 로그"의 신뢰성을 소폭 훼손.

## 반드시 고칠 것 (Actionable)

1. **05_run.md 70행**: "실제 실행 로그(발췌)" 블록의 마지막 줄을 프로그램이 실제로 찍는 문자열로 교체하거나(`DEMO COMPLETE - all assertions passed` + `Proved ...` 줄), `EXIT=0`/`ELAPSED_SEC=1.12`는 발췌 블록 밖에서 "실행 소요 약 1.1초, 종료코드 0(외부 측정)"으로 분리 표기해 프로그램 출력과 실측치를 구분할 것. (해석 결론 자체는 유효하므로 표현만 정정.)

## 권장 개선 (선택)

1. 재현성을 더 강화하려면 `run_demo.py`에 실제 elapsed/exit 출력(예: `time.perf_counter()` 측정 후 `ELAPSED_SEC=` 프린트)을 넣어 리포트 로그와 코드 출력이 문자 그대로 일치하게 만들 것.
2. `requirements.txt`에 검증된 정확 버전(Pillow 9.4.0)을 핀 코멘트로 남기면 환경 재현이 더 견고.
3. Part D 로그의 sec3(질의 있음에도 모델이 silence 선택) 케이스가 "질의≠강제응답"을 보여주는 핵심 반례이므로, 결과 해석에서 한 줄 더 강조하면 논문 대응이 선명.

---
판정: PASS ✅ (8/10) · 저장 경로: C:/Users/wagra/claude/code/output/joyaivl_2606.14777/feedback_run.md


===================================================================
# 피드백: html

- **판정**: PASS ✅  (점수: 7/10)

대상: `./output/joyaivl_2606.14777/report.html` (750줄)

## 항목별 평가

- [x] **자급식(인라인 CSS)** — 리포트 디자인 스타일은 `<style>` 블록에 전부 인라인. 토큰(`--bg/--surface/--border/--text/--muted/--accent/--radius/--maxw`) 정의됨. (단, 수식 렌더링 KaTeX는 CDN 외부 의존 — 아래 권장 개선)
- [x] **브라우저에서 열림** — `<!doctype html>` + 유효한 구조, 닫힘 태그 정합. 정상 로드.
- [x] **디자인 규약 준수** — 순백 배경 `--bg:#FFFFFF`, 본문폭 `--maxw:720px`, 본문 대비 `--text:#1A1A1A`/`--muted:#5B6470` 충분. 카드·badge·table·blockquote·pre 일관.
- [x] **TOC 존재** — `<nav class="toc card">`에 3개 대분류 + 하위 항목. TOC 링크 16개 앵커가 모두 대응 `id`에 매칭됨(검증 완료: 16/16).
- [x] **콘텐츠 완전성** — Analysis(요약/문제/한계/방법/기여/실험/한계/구현단서), Detail(3개념 4단 구조+FAQ), Code(18행 매핑표+Part0/1/2) 모두 병합·정상.
- [ ] **수식 렌더링 정확성** — 미달. 아래 필수 수정 참조.

## 반드시 고칠 것 (Actionable)

1. **259행 수식 내 결정 토큰이 브라우저에서 빈칸으로 렌더링됨 (실질 버그).**
   ```
   259: <p>$$a_t \in \{\ \texttt{</silence>},\ \ \texttt{</response>},\ \ \texttt{</delegate>}\ \}$$</p>
   ```
   `</silence>` `</response>` `</delegate>`가 **미이스케이프 원시 태그**로 들어가 있음. HTML5 파서가 이를 미지의 종료 태그로 간주해 DOM에서 제거 → KaTeX가 읽는 textContent는 `\texttt{}` 3개가 전부 **빈 상자**가 됨. 즉 핵심 "3지선다" 수식에서 `</silence>/</response>/</delegate>` 글자가 화면에 안 보임.
   - 수정: 나머지 본문(224/301행 등)처럼 `&lt;/silence&gt;`, `&lt;/response&gt;`, `&lt;/delegate&gt;`로 이스케이프. 즉:
     `$$a_t \in \{\ \texttt{&lt;/silence&gt;},\ \ \texttt{&lt;/response&gt;},\ \ \texttt{&lt;/delegate&gt;}\ \}$$`
   - 이 파일에서 각괄호 태그가 이스케이프 안 된 유일한 지점(다른 곳은 전부 `&lt; &gt;` 처리됨).

## 권장 개선 (선택)

- **KaTeX 외부 의존**: 735~736행이 CDN에서 KaTeX JS/CSS를 로드. 오프라인/무네트워크 환경에서는 수식이 `$…$` 원문으로 노출됨. 완전 자급식을 원하면 KaTeX min.css/js를 인라인하거나, 서버 사이드 프리렌더 고려. (현 체크리스트의 "인라인 CSS" 규약은 리포트 스타일 기준으로는 충족.)
- 259행 외 다른 `$$…$$` 블록(263/271/351/369행 등)은 각괄호 태그를 포함하지 않아 문제 없음.

**판정: PASS ✅ (7/10) — 저장 경로: `./output/joyaivl_2606.14777/feedback_html.md`**

