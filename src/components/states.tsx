"use client";

// 공통 상태 UI: 로딩 스켈레톤 / 빈 상태 / 에러 상태.
// "행복 경로만 = 미완성" 원칙에 따라 모든 화면이 이 세 상태를 갖춘다.
import type { ReactNode } from "react";

/** 카드 리스트용 로딩 스켈레톤 */
export function CardSkeletonList({ count = 4 }: { count?: number }) {
  return (
    <ul className="cardList" aria-hidden="true">
      {Array.from({ length: count }).map((_, i) => (
        <li key={i} className="card card--skeleton">
          <div className="sk sk--line sk--w40" />
          <div className="sk sk--line sk--w80" />
          <div className="sk sk--chips">
            <span className="sk sk--chip" />
            <span className="sk sk--chip" />
          </div>
        </li>
      ))}
    </ul>
  );
}

/** 빈 상태 — 무엇이 없고 다음에 뭘 하면 되는지 안내 */
export function EmptyState({
  title,
  description,
  action,
}: {
  title: string;
  description?: string;
  action?: ReactNode;
}) {
  return (
    <div className="state" role="status">
      <div className="state__icon" aria-hidden="true">
        📭
      </div>
      <h2 className="state__title">{title}</h2>
      {description && <p className="state__desc">{description}</p>}
      {action && <div className="state__action">{action}</div>}
    </div>
  );
}

/** 에러 상태 — 사람이 읽을 메시지 + 재시도 */
export function ErrorState({
  title = "문제가 생겼어요",
  message,
  onRetry,
  action,
}: {
  title?: string;
  message?: string;
  onRetry?: () => void;
  action?: ReactNode;
}) {
  return (
    <div className="state state--error" role="alert">
      <div className="state__icon" aria-hidden="true">
        ⚠️
      </div>
      <h2 className="state__title">{title}</h2>
      {message && <p className="state__desc">{message}</p>}
      <div className="state__action">
        {onRetry && (
          <button type="button" className="btn btn--primary" onClick={onRetry}>
            다시 시도
          </button>
        )}
        {action}
      </div>
    </div>
  );
}
