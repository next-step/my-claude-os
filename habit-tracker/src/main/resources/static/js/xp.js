/**
 * xp.js — XP 프로그레스 바 애니메이션 + 레벨업 배너 제어
 *
 * CSS transition은 "초기값 → 변경값" 흐름에서만 작동한다.
 * 서버 렌더링으로 width를 바로 세팅하면 transition이 발동하지 않으므로,
 * HTML에서는 data-percent만 주입하고 JS가 DOM 로드 후 width에 주입한다.
 */
document.addEventListener('DOMContentLoaded', () => {

  // ── XP 프로그레스 바 0% → 실제값 애니메이션 ──────────────────
  const fill = document.querySelector('.xp-progress-fill');
  if (fill) {
    // data-percent 속성값을 width에 주입 → CSS transition 0.4s ease-out 발동
    fill.style.width = fill.dataset.percent + '%';
  }

  // ── 레벨업 배너 슬라이드 인 + 자동 페이드 아웃 ──────────────────
  const banner = document.querySelector('.level-up-banner');
  if (banner) {

    // translateY(-100%) → translateY(0): 위에서 슬라이드 인 (0.3s ease-out)
    // requestAnimationFrame으로 한 프레임 뒤에 클래스를 추가해야 transition이 발동한다
    requestAnimationFrame(() => banner.classList.add('visible'));

    // 4.5초 후 opacity 0 페이드 시작, transitionend에서 DOM 제거 (총 5초)
    const fadeTimer = setTimeout(() => {
      banner.classList.add('fading');
      banner.addEventListener('transitionend', () => banner.remove(), { once: true });
    }, 4500);

    // 닫기 버튼: fadeTimer 취소 후 즉시 DOM 제거
    const closeBtn = banner.querySelector('.banner-close');
    if (closeBtn) {
      closeBtn.addEventListener('click', () => {
        clearTimeout(fadeTimer);
        banner.remove();
      });
    }
  }
});
