package com.habit.tracker.service;

import com.habit.tracker.domain.Level;
import com.habit.tracker.domain.UserStats;
import com.habit.tracker.repository.UserStatsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

/**
 * UserStatsService 단위 테스트
 *
 * Repository 는 Mockito 로 목킹 → 순수 비즈니스 로직만 검증
 * UserStats 는 id=1L 싱글턴이며, 없으면 새로 생성된다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserStatsService")
class UserStatsServiceTest {

    @Mock
    private UserStatsRepository userStatsRepository;

    @InjectMocks
    private UserStatsService userStatsService;

    // ── 픽스처 헬퍼 ──────────────────────────────────────────────────────────

    /** totalXp 를 가진 UserStats 픽스처 */
    private UserStats statsWithXp(int totalXp) {
        UserStats stats = new UserStats();
        stats.setTotalXp(totalXp);
        return stats;
    }

    // ── getOrCreate (싱글턴 보장) ─────────────────────────────────────────────

    @Nested
    @DisplayName("싱글턴 보장 — id=1L 없으면 자동 생성")
    class GetOrCreate {

        @Test
        @DisplayName("DB 에 UserStats 가 없으면 save 를 호출해 새 인스턴스를 생성한다")
        void DB에없으면새_인스턴스생성() {
            given(userStatsRepository.findById(1L)).willReturn(Optional.empty());
            given(userStatsRepository.save(any(UserStats.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            int xp = userStatsService.getTotalXp();

            then(userStatsRepository).should().save(any(UserStats.class));
            assertThat(xp).isZero();
        }

        @Test
        @DisplayName("DB 에 UserStats 가 있으면 기존 인스턴스를 사용한다")
        void DB에있으면기존_인스턴스사용() {
            UserStats existing = statsWithXp(500);
            given(userStatsRepository.findById(1L)).willReturn(Optional.of(existing));

            int xp = userStatsService.getTotalXp();

            then(userStatsRepository).should(times(0)).save(any());
            assertThat(xp).isEqualTo(500);
        }
    }

    // ── awardXp ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("awardXp — XP 적립")
    class AwardXp {

        @Test
        @DisplayName("awardXp(10) 호출 시 totalXp 가 10 증가한다")
        void XP_10_증가() {
            UserStats stats = statsWithXp(0);
            given(userStatsRepository.findById(1L)).willReturn(Optional.of(stats));

            userStatsService.awardXp(10);

            assertThat(stats.getTotalXp()).isEqualTo(10);
        }

        @Test
        @DisplayName("awardXp 호출 후 updatedAt 이 설정된다")
        void XP_적립후_updatedAt_설정() {
            UserStats stats = statsWithXp(0);
            given(userStatsRepository.findById(1L)).willReturn(Optional.of(stats));

            userStatsService.awardXp(10);

            assertThat(stats.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("awardXp 를 여러 번 호출하면 누적된다")
        void XP_누적_적립() {
            UserStats stats = statsWithXp(0);
            given(userStatsRepository.findById(1L)).willReturn(Optional.of(stats));

            userStatsService.awardXp(10);
            userStatsService.awardXp(50);

            assertThat(stats.getTotalXp()).isEqualTo(60);
        }
    }

    // ── getCurrentLevel ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("getCurrentLevel — 현재 레벨 계산")
    class GetCurrentLevel {

        @Test
        @DisplayName("totalXp = 0 이면 SPROUT 를 반환한다")
        void XP_0이면_SPROUT() {
            given(userStatsRepository.findById(1L)).willReturn(Optional.of(statsWithXp(0)));
            assertThat(userStatsService.getCurrentLevel()).isEqualTo(Level.SPROUT);
        }

        @Test
        @DisplayName("totalXp = 200 이면 SAPLING 을 반환한다")
        void XP_200이면_SAPLING() {
            given(userStatsRepository.findById(1L)).willReturn(Optional.of(statsWithXp(200)));
            assertThat(userStatsService.getCurrentLevel()).isEqualTo(Level.SAPLING);
        }

        @Test
        @DisplayName("totalXp = 15000 이면 LEGEND 를 반환한다")
        void XP_15000이면_LEGEND() {
            given(userStatsRepository.findById(1L)).willReturn(Optional.of(statsWithXp(15000)));
            assertThat(userStatsService.getCurrentLevel()).isEqualTo(Level.LEGEND);
        }
    }

    // ── getXpInCurrentLevel ───────────────────────────────────────────────────

    @Nested
    @DisplayName("getXpInCurrentLevel — 현재 레벨 내 진행 XP")
    class GetXpInCurrentLevel {

        @Test
        @DisplayName("totalXp = 250 이면 SAPLING(200) 기준 50XP 진행")
        void SAPLING_진행XP_50() {
            given(userStatsRepository.findById(1L)).willReturn(Optional.of(statsWithXp(250)));
            assertThat(userStatsService.getXpInCurrentLevel()).isEqualTo(50);
        }

        @Test
        @DisplayName("totalXp = 0 이면 SPROUT 기준 0XP 진행")
        void SPROUT_진행XP_0() {
            given(userStatsRepository.findById(1L)).willReturn(Optional.of(statsWithXp(0)));
            assertThat(userStatsService.getXpInCurrentLevel()).isEqualTo(0);
        }
    }

    // ── getXpToNextLevel ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("getXpToNextLevel — 다음 레벨까지 남은 XP")
    class GetXpToNextLevel {

        @Test
        @DisplayName("totalXp = 0 이면 SAPLING(200) 까지 200 XP 남음")
        void SPROUT에서_다음레벨까지_200() {
            given(userStatsRepository.findById(1L)).willReturn(Optional.of(statsWithXp(0)));
            assertThat(userStatsService.getXpToNextLevel()).isEqualTo(200);
        }

        @Test
        @DisplayName("totalXp = 250 이면 TREE(600) 까지 350 XP 남음")
        void SAPLING_중간에서_다음레벨까지() {
            given(userStatsRepository.findById(1L)).willReturn(Optional.of(statsWithXp(250)));
            assertThat(userStatsService.getXpToNextLevel()).isEqualTo(350);
        }

        @Test
        @DisplayName("LEGEND(만렙) 이면 0을 반환한다")
        void LEGEND이면_0반환() {
            given(userStatsRepository.findById(1L)).willReturn(Optional.of(statsWithXp(15000)));
            assertThat(userStatsService.getXpToNextLevel()).isEqualTo(0);
        }
    }

    // ── getXpProgressPercent ──────────────────────────────────────────────────

    @Nested
    @DisplayName("getXpProgressPercent — 현재 레벨 구간 내 진행률 0~100")
    class GetXpProgressPercent {

        @Test
        @DisplayName("SPROUT 0XP 이면 진행률 0")
        void SPROUT_0XP_진행률_0() {
            given(userStatsRepository.findById(1L)).willReturn(Optional.of(statsWithXp(0)));
            assertThat(userStatsService.getXpProgressPercent()).isEqualTo(0);
        }

        @Test
        @DisplayName("SPROUT 구간(0~200) 에서 100XP 이면 진행률 50")
        void SPROUT_100XP_진행률_50() {
            given(userStatsRepository.findById(1L)).willReturn(Optional.of(statsWithXp(100)));
            assertThat(userStatsService.getXpProgressPercent()).isEqualTo(50);
        }

        @Test
        @DisplayName("SAPLING 구간(200~600) 에서 400XP 이면 진행률 50")
        void SAPLING_400XP_진행률_50() {
            // 400XP = SAPLING 시작(200) + 200XP 진행 → 구간(400) 중 200 → 50%
            given(userStatsRepository.findById(1L)).willReturn(Optional.of(statsWithXp(400)));
            assertThat(userStatsService.getXpProgressPercent()).isEqualTo(50);
        }

        @Test
        @DisplayName("LEGEND 만렙이면 진행률 100")
        void LEGEND_진행률_100() {
            given(userStatsRepository.findById(1L)).willReturn(Optional.of(statsWithXp(15000)));
            assertThat(userStatsService.getXpProgressPercent()).isEqualTo(100);
        }
    }

    // ── isTodayBonusAwarded / markTodayBonusAwarded ───────────────────────────

    @Nested
    @DisplayName("오늘 보너스 중복 방지")
    class TodayBonus {

        @Test
        @DisplayName("lastBonusDate 가 null 이면 오늘 보너스 미수령으로 판단한다")
        void lastBonusDate_null이면_미수령() {
            UserStats stats = new UserStats();
            given(userStatsRepository.findById(1L)).willReturn(Optional.of(stats));

            assertThat(userStatsService.isTodayBonusAwarded()).isFalse();
        }

        @Test
        @DisplayName("lastBonusDate 가 오늘이면 오늘 보너스 이미 수령으로 판단한다")
        void lastBonusDate_오늘이면_수령() {
            UserStats stats = new UserStats();
            stats.setLastBonusDate(LocalDate.now());
            given(userStatsRepository.findById(1L)).willReturn(Optional.of(stats));

            assertThat(userStatsService.isTodayBonusAwarded()).isTrue();
        }

        @Test
        @DisplayName("lastBonusDate 가 어제이면 오늘 보너스 미수령으로 판단한다")
        void lastBonusDate_어제이면_미수령() {
            UserStats stats = new UserStats();
            stats.setLastBonusDate(LocalDate.now().minusDays(1));
            given(userStatsRepository.findById(1L)).willReturn(Optional.of(stats));

            assertThat(userStatsService.isTodayBonusAwarded()).isFalse();
        }

        @Test
        @DisplayName("markTodayBonusAwarded 호출 시 lastBonusDate 가 오늘로 설정된다")
        void markTodayBonusAwarded_오늘로설정() {
            UserStats stats = new UserStats();
            given(userStatsRepository.findById(1L)).willReturn(Optional.of(stats));

            userStatsService.markTodayBonusAwarded();

            assertThat(stats.getLastBonusDate()).isEqualTo(LocalDate.now());
        }
    }
}
