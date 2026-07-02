"""skill_stats.count_skills 의 정상/엣지 케이스 검증 (unittest)."""
import os
import sys
import unittest

# scripts/ 를 import 경로에 추가 (외부 의존성 없이 표준 라이브러리만 사용)
sys.path.insert(
    0,
    os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "scripts"),
)

from skill_stats import count_skills, top_skills  # noqa: E402


class TestCountSkills(unittest.TestCase):
    def test_기본_집계(self):
        # 시각 열에 공백이 있으므로 탭 기준으로 나눠야 정상 집계된다
        lines = [
            "2026-06-25 20:34:11\tgit-commit",
            "2026-06-25 20:34:11\tgit-commit",
            "2026-06-25 20:34:11\tdeep-research",
            "2026-07-02 08:43:54\tfeature-dev",
        ]
        self.assertEqual(
            count_skills(lines),
            {"git-commit": 2, "deep-research": 1, "feature-dev": 1},
        )

    def test_빈_로그(self):
        self.assertEqual(count_skills([]), {})

    def test_빈_줄과_공백_줄_무시(self):
        lines = [
            "",
            "   ",
            "\n",
            "2026-06-25 20:34:11\tgit-commit",
        ]
        self.assertEqual(count_skills(lines), {"git-commit": 1})

    def test_탭_없는_형식깨진_줄_무시(self):
        lines = [
            "형식이 깨진 줄 공백만 있음",
            "2026-06-25 20:34:11 git-commit",  # 공백 구분(탭 아님) → 무시
            "2026-06-25 20:34:11\tgit-commit",
        ]
        self.assertEqual(count_skills(lines), {"git-commit": 1})

    def test_스킬이름_빈_줄_무시(self):
        lines = [
            "2026-06-25 20:34:11\t",       # 스킬이름 없음
            "2026-06-25 20:34:11\t   ",    # 공백뿐 → 빈 이름 취급
            "2026-06-25 20:34:11\tgit-commit",
        ]
        self.assertEqual(count_skills(lines), {"git-commit": 1})

    def test_trailing_개행으로_생긴_빈_문자열(self):
        # 파일을 splitlines/split('\n') 하면 마지막에 빈 문자열이 생길 수 있다
        raw = "2026-06-25 20:34:11\tgit-commit\n"
        self.assertEqual(count_skills(raw.split("\n")), {"git-commit": 1})

    def test_줄_끝_개행_포함해도_이름_정확(self):
        # 각 줄에 개행이 남아 있어도 스킬이름이 오염되지 않아야 한다
        lines = ["2026-06-25 20:34:11\tgit-commit\n"]
        self.assertEqual(count_skills(lines), {"git-commit": 1})

    def test_스킬이름에_추가_탭_있어도_두번째_필드만(self):
        # 예기치 못한 추가 탭이 있어도 2번째 필드를 스킬이름으로 본다
        lines = ["2026-06-25 20:34:11\tgit-commit\textra"]
        self.assertEqual(count_skills(lines), {"git-commit": 1})


class TestTopSkills(unittest.TestCase):
    # 공통 샘플: 정렬 규칙(횟수 내림차순, 동수면 이름 오름차순)이
    # 잘 드러나도록 동수(1회)인 항목을 이름이 뒤섞이게 배치했다.
    SAMPLE = {"git-commit": 3, "feature-dev": 2, "deep-research": 1, "aaa": 1}

    def test_N이_None이면_전체를_정렬해_반환(self):
        # 기존 동작과 동일하게 전체를 정렬만 해서 돌려준다
        self.assertEqual(
            top_skills(self.SAMPLE, None),
            [("git-commit", 3), ("feature-dev", 2), ("aaa", 1), ("deep-research", 1)],
        )

    def test_상위_N개_선별_정확성(self):
        self.assertEqual(
            top_skills(self.SAMPLE, 2),
            [("git-commit", 3), ("feature-dev", 2)],
        )

    def test_동수면_이름_오름차순_tie_break(self):
        # 1회로 동수인 aaa/deep-research 중 이름이 앞선 aaa가 먼저 온다
        self.assertEqual(
            top_skills(self.SAMPLE, 3),
            [("git-commit", 3), ("feature-dev", 2), ("aaa", 1)],
        )

    def test_N이_전체보다_크면_전체_반환(self):
        self.assertEqual(len(top_skills(self.SAMPLE, 99)), len(self.SAMPLE))

    def test_N이_0이면_빈_리스트(self):
        self.assertEqual(top_skills(self.SAMPLE, 0), [])

    def test_음수_N은_빈_리스트_slice버그_방어(self):
        # 음수를 slice에 그대로 넘기면 뒤에서 잘려 오작동한다.
        # 음수는 0과 동일하게 취급해 빈 리스트를 반환해야 한다.
        self.assertEqual(top_skills(self.SAMPLE, -1), [])
        self.assertEqual(top_skills(self.SAMPLE, -100), [])

    def test_빈_집계는_항상_빈_리스트(self):
        self.assertEqual(top_skills({}, None), [])
        self.assertEqual(top_skills({}, 5), [])

    def test_비정수_N은_TypeError(self):
        # 순수 함수 단독 사용 시 잘못된 타입을 명확히 거부한다
        with self.assertRaises(TypeError):
            top_skills(self.SAMPLE, "2")
        with self.assertRaises(TypeError):
            top_skills(self.SAMPLE, 1.5)
        # bool은 int의 하위 타입이지만 개수 의미가 아니므로 거부한다
        with self.assertRaises(TypeError):
            top_skills(self.SAMPLE, True)


if __name__ == "__main__":
    unittest.main()
