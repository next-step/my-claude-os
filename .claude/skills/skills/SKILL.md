---
name: skills
description: 이 프로젝트에 등록된 스킬을 모두 스캔해 이름·설명·호출 방식과 함께 한눈에 보여준다.
user-invocable: true
allowed-tools: Bash
---

# /skills 스킬 — 사용 가능한 스킬 카탈로그

이 레포의 `.claude/skills/` 아래에 등록된 **모든 스킬**을 스캔해서,
무엇을 `/이름` 으로 부를 수 있는지 한눈에 보여준다.

스킬을 추가/삭제한 뒤 "지금 뭐가 있더라?"가 궁금할 때 쓴다.

## 사용법

```
/skills            ← 등록된 모든 스킬을 이름·설명과 함께 표시
/skills 노션       ← 이름·설명에 키워드가 들어간 스킬만 검색
```

---

## 실행 절차

> **설계 포인트 — 정본(source of truth)을 읽는다**
> 스킬 목록을 문서에 손으로 적어두면 새 스킬을 추가할 때마다 낡는다.
> 이 스킬은 옛 목록을 믿지 않고, 항상 `.claude/skills/*/SKILL.md`의
> frontmatter를 직접 스캔해 거기서 사실을 다시 길어 올린다.
> ([[remind-when]]이 crontab 정본을, [[sync-readme]]가 파일시스템을 직접 읽는 것과 같은 철학.)
>
> **결정론적 조회 → 직접 처리**
> 스캔·포맷은 입력이 정해지면 출력도 정해지는 일이라 Agent가 필요 없다.
> [[list]]가 할일을 직접 묶어 출력하듯, 오케스트레이터가 Bash 한 번으로 처리한다.
>
> **속도의 진짜 병목은 Bash가 아니라 LLM 왕복이다**
> 9개 파일 스캔은 awk 1패스로 4ms면 끝난다(파일당 awk 3번 = 27회 spawn 하던 옛
> 방식도 고작 40ms였다). 체감 지연의 거의 전부는 ① Bash 호출을 생성하는 턴과
> ② 결과를 다시 포맷팅하는 턴, 이 **LLM 생성 2번**이다. 그래서 이 스킬은
> Bash 출력을 *완성본*으로 만들고, 오케스트레이터는 그걸 **한 글자도 바꾸지 말고
> 그대로 내보내** 두 번째 턴을 없앤다. (스크립트 미세 최적화보다 이게 훨씬 크다.)

### Step 1: 스킬 스캔 + 포맷 출력 (awk 1패스)

인자가 있으면 `$1`을 키워드로, 없으면 전체를 보여준다.
아래 Bash를 실행한다. **출력은 이미 완성된 최종 메시지이므로, 표로 다시 그리거나
재포맷하지 말고 그대로(verbatim) 사용자에게 relay한다** — 그래야 두 번째 LLM 턴이
사라져 체감 속도가 빨라진다. (키워드는 첫 인자로 전달한다. 예: `keyword="노션"`)

```bash
keyword="${1:-}"

# 모든 SKILL.md를 awk '단 한 번'으로 스캔·필터·포맷한다.
# self(=이 skills 스킬)도 제외하지 않고 함께 보여준다.
# macOS 기본 awk(BWK awk)에는 gawk 전용 ENDFILE이 없으므로,
# "FNR==1에서 직전 파일을 flush"하는 이식성 있는 관용구를 쓴다.
awk -v kw="$keyword" '
  function flush(   hay, needle, tag) {
    if (name == "") return                 # SKILL.md 없는 _shared/ 등은 스킵
    total++
    if (kw != "") {                        # 키워드 필터: 이름+설명 부분일치(대소문자 무시)
      hay = tolower(name " " desc)
      needle = tolower(kw)
      if (index(hay, needle) == 0) return
    }
    tag = (inv == "false") ? " [내부]" : ""  # user-invocable:false → 내부용 꼬리표
    out = out "  • /" name tag " — " desc "\n"
    shown++
  }
  FNR == 1 && started { flush(); name=""; desc=""; inv="" }  # 새 파일 시작 → 직전 파일 마감
  FNR == 1 { started = 1 }
  /^name:/           && name == "" { line=$0; sub(/^name:[ \t]*/,          "", line); name = line }
  /^description:/    && desc == "" { line=$0; sub(/^description:[ \t]*/,   "", line); desc = line }
  /^user-invocable:/ && inv  == "" { line=$0; sub(/^user-invocable:[ \t]*/,"", line); inv  = line }
  END {
    flush()                                # 마지막 파일 마감
    if (kw != "") printf "🔍 '\''%s'\'' 검색 결과 (%d/%d개)\n\n", kw, shown, total
    else          printf "🧰 사용 가능한 스킬 (%d개)\n\n", total
    if (shown == 0) {
      if (kw != "") printf "  '\''%s'\''에 해당하는 스킬이 없어요.\n", kw
      else          printf "  아직 등록된 스킬이 없어요. .claude/skills/ 아래에 SKILL.md를 추가해보세요.\n"
    } else {
      printf "%s", out
      printf "\n  각 스킬은 /이름 으로 호출해요. 자세한 사용법은 해당 SKILL.md를 보세요.\n"
    }
  }
' .claude/skills/*/SKILL.md
```

> `_shared/` 처럼 SKILL.md가 없는 헬퍼 디렉터리는 자동으로 스킵된다(글롭이 `SKILL.md`만 본다).

---

## 설계 노트 — 왜 이렇게 만들었나

- **AI 협업 학습 포인트**: "목록 보여주기"는 ① 사실 수집과 ② 정해진 포맷 출력의
  합성이다. 둘 다 결정론적이라 한 Bash 안에서 끝낸다 — 여기에 Agent를 띄우면
  콜드 스타트·프롬프트 재독해 비용만 늘 뿐 결과는 더 정확해지지 않는다.
- **최적화는 측정한 곳에만**: 처음엔 파일당 awk 3번(27회 프로세스 spawn)이었지만,
  실측해보니 40ms → 4ms로 줄여도 사람은 못 느낀다. 진짜 병목은 LLM 왕복이었다.
  그래서 (a) 스크립트는 awk 1패스로 단순화하되, (b) 더 큰 이득인 "출력을 그대로
  relay해 두 번째 LLM 턴 제거"에 무게를 뒀다. **추측 대신 측정이 우선순위를 정한다.**
- **이름 충돌 회피**: `/list`는 *할일*을 보여주는 스킬이라, *스킬 목록*은 `/skills`로
  분리했다. 같은 "보여주기"라도 대상이 다르면 다른 커맨드로 둔다.
- 이 카탈로그 덕분에 새 스킬을 추가한 직후 `/skills` 한 번으로 잘 등록됐는지
  바로 확인할 수 있다. ([[sync-readme]]가 문서를 맞춰준다면, 이건 즉석 점검용.)
