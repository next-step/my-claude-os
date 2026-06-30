package com.habit.tracker.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Level 열거형")
class LevelTest {

    // ── 레벨 상수 정의 검증 ──────────────────────────────────────────────────

    @Nested
    @DisplayName("레벨 속성 값이 설계대로 정의된다")
    class LevelAttributes {

        @Test
        @DisplayName("SPROUT 는 레벨 1 새싹 이모지 0XP 에서 시작한다")
        void SPROUT_속성_검증() {
            assertThat(Level.SPROUT.getNumber()).isEqualTo(1);
            assertThat(Level.SPROUT.getName()).isEqualTo("새싹");
            assertThat(Level.SPROUT.getEmoji()).isEqualTo("🌱");
            assertThat(Level.SPROUT.getMinXp()).isEqualTo(0);
        }

        @Test
        @DisplayName("SAPLING 은 레벨 2 묘목 200XP 이상에서 시작한다")
        void SAPLING_속성_검증() {
            assertThat(Level.SAPLING.getNumber()).isEqualTo(2);
            assertThat(Level.SAPLING.getName()).isEqualTo("묘목");
            assertThat(Level.SAPLING.getEmoji()).isEqualTo("🌿");
            assertThat(Level.SAPLING.getMinXp()).isEqualTo(200);
        }

        @Test
        @DisplayName("LEGEND 는 레벨 7 전설 15000XP 이상에서 시작한다")
        void LEGEND_속성_검증() {
            assertThat(Level.LEGEND.getNumber()).isEqualTo(7);
            assertThat(Level.LEGEND.getName()).isEqualTo("전설");
            assertThat(Level.LEGEND.getEmoji()).isEqualTo("⚡");
            assertThat(Level.LEGEND.getMinXp()).isEqualTo(15000);
        }

        @Test
        @DisplayName("레벨 열거값이 총 7개다")
        void 레벨은_7개() {
            assertThat(Level.values()).hasSize(7);
        }
    }

    // ── fromXp 경계값 테스트 ─────────────────────────────────────────────────

    @Nested
    @DisplayName("fromXp 는 누적 XP 에 맞는 레벨을 반환한다")
    class FromXp {

        @ParameterizedTest(name = "totalXp={0} → {1}")
        @CsvSource({
                "0,     SPROUT",
                "1,     SPROUT",
                "199,   SPROUT",
                "200,   SAPLING",
                "599,   SAPLING",
                "600,   TREE",
                "1499,  TREE",
                "1500,  FOREST",
                "3499,  FOREST",
                "3500,  GARDENER",
                "6999,  GARDENER",
                "7000,  NATURALIST",
                "14999, NATURALIST",
                "15000, LEGEND",
                "99999, LEGEND"
        })
        @DisplayName("XP 경계값마다 올바른 레벨을 반환한다")
        void XP경계값마다올바른레벨반환(int totalXp, String expectedLevelName) {
            Level expected = Level.valueOf(expectedLevelName);
            assertThat(Level.fromXp(totalXp)).isEqualTo(expected);
        }

        @Test
        @DisplayName("XP 0 이면 SPROUT 를 반환한다 — 신규 사용자 초기 상태")
        void XP_0이면_SPROUT() {
            assertThat(Level.fromXp(0)).isEqualTo(Level.SPROUT);
        }

        @Test
        @DisplayName("XP 가 15000 이상이면 항상 LEGEND 를 반환한다")
        void XP_초과해도_LEGEND_유지() {
            assertThat(Level.fromXp(15000)).isEqualTo(Level.LEGEND);
            assertThat(Level.fromXp(50000)).isEqualTo(Level.LEGEND);
        }
    }

    // ── nextLevel 헬퍼 ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("nextLevel 은 다음 레벨 또는 만렙 시 자기 자신을 반환한다")
    class NextLevel {

        @Test
        @DisplayName("SPROUT 의 다음 레벨은 SAPLING 이다")
        void SPROUT_다음레벨_SAPLING() {
            assertThat(Level.SPROUT.nextLevel()).isEqualTo(Level.SAPLING);
        }

        @Test
        @DisplayName("LEGEND 의 다음 레벨은 LEGEND 자기 자신이다 — 만렙 처리")
        void LEGEND_다음레벨_자기자신() {
            assertThat(Level.LEGEND.nextLevel()).isEqualTo(Level.LEGEND);
        }

        @Test
        @DisplayName("NATURALIST 의 다음 레벨은 LEGEND 다")
        void NATURALIST_다음레벨_LEGEND() {
            assertThat(Level.NATURALIST.nextLevel()).isEqualTo(Level.LEGEND);
        }
    }
}
