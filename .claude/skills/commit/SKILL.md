---
name: commit
description: Stage and commit the current working-tree changes with a clean, conventional commit message. Use when the user asks to commit, save progress to git, or after a paper-os cycle finishes to commit the new outputs. Optionally pushes when asked.
---

# /commit — 커밋 스킬

작업 트리의 변경사항을 검토해 **깔끔한 커밋 메시지**로 커밋합니다.
`paper-os` 사이클이 끝난 뒤 새 논문 산출물을 저장하는 데도 사용합니다.

## 절차
1. **상태 확인**: 병렬로 실행
   - `git -C "<repo>" status --short`
   - `git -C "<repo>" diff --stat`
   - 최근 스타일 참고: `git -C "<repo>" log --oneline -5`
2. **제외 확인**: 민감/머신별 파일(`settings.local.json` 등)이 `.gitignore`로 빠졌는지 확인. 실수로 커밋될 것 같으면 사용자에게 알린다.
3. **스테이징**: 논리적으로 관련된 변경을 함께 `git add`. (기본은 `git add -A`, 단 무관한 변경이 섞였으면 파일 단위로.)
4. **메시지 작성**: 아래 규칙에 맞춰.
5. **커밋**: `git commit -F -` 로 heredoc 메시지 전달(따옴표 이스케이프 문제 회피).
6. **보고**: `git log --oneline -1` 로 결과 확인 후 커밋 해시·요약을 보고.
7. **푸시**: 사용자가 요청했거나 "커밋하고 올려줘" 맥락이면 `git push origin <현재브랜치>`. 아니면 푸시하지 않고 "푸시할까요?"로 마무리.

## 커밋 메시지 규칙
```
<요약 한 줄: 명령형, ~72자, 무엇을 왜>

<본문(선택): 무엇을/왜 바뀌었는지 불릿 몇 줄>

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
```
- 요약은 명령형("Add…", "Fix…", "Organize…"), 마침표 없이.
- paper-os 산출 커밋이면 요약에 논문 slug/제목을 넣는다.
  예: `Add analysis outputs for joyai-vl (2606.14777)`
- 본문에는 어떤 단계가 PASS/FAIL이었는지, 새 폴더 경로 등 유용한 맥락을 적는다.

## 규칙
- **기본 브랜치(main 등)에 직접 커밋 금지**가 필요한 상황이면 먼저 브랜치를 판다. 현재 저장소는 작업 브랜치(`step2-1` 등)에서 진행 중이면 그대로 커밋.
- 훅(hook)을 우회하지 말 것(`--no-verify` 금지). 훅 실패 시 원인을 고친다.
- 커밋할 변경이 없으면 그 사실만 보고하고 종료.
- 사용자가 명시적으로 요청하지 않는 한 amend/force-push 하지 않는다.
