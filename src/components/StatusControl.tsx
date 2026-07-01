"use client";

// 북마크 지원 상태(지원예정/지원함/마감) 변경 컨트롤. 저장 화면·상세에서 공용.
import type { BookmarkStatus } from "@/types/contract";
import { useBookmarks } from "@/lib/bookmarks";

const OPTIONS: { value: BookmarkStatus; label: string }[] = [
  { value: "PLANNED", label: "지원 예정" },
  { value: "APPLIED", label: "지원함" },
  { value: "CLOSED", label: "마감" },
];

export default function StatusControl({
  jobId,
  status,
}: {
  jobId: string;
  status: BookmarkStatus;
}) {
  const { setStatus } = useBookmarks();
  return (
    <div className="segmented segmented--sm" role="group" aria-label="지원 상태">
      {OPTIONS.map((o) => (
        <button
          key={o.value}
          type="button"
          className={`segmented__btn${
            status === o.value ? " segmented__btn--on" : ""
          }`}
          aria-pressed={status === o.value}
          onClick={() => setStatus(jobId, o.value)}
        >
          {o.label}
        </button>
      ))}
    </div>
  );
}
