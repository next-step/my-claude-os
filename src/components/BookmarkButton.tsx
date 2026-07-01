"use client";

// 북마크 토글 버튼. 카드의 stretched-link 위에 떠야 하므로 z-index 를 갖는다.
import { useBookmarks } from "@/lib/bookmarks";

export default function BookmarkButton({
  jobId,
  size = "md",
}: {
  jobId: string;
  size?: "md" | "lg";
}) {
  const { isBookmarked, toggle, ready } = useBookmarks();
  const saved = isBookmarked(jobId);

  return (
    <button
      type="button"
      className={`bookmarkBtn bookmarkBtn--${size}${
        saved ? " bookmarkBtn--on" : ""
      }`}
      aria-pressed={saved}
      aria-label={saved ? "북마크 해제" : "북마크에 저장"}
      title={saved ? "북마크 해제" : "북마크에 저장"}
      disabled={!ready}
      onClick={(e) => {
        // 카드 전체 링크(stretched link) 클릭으로 전파되지 않게 막는다.
        e.preventDefault();
        e.stopPropagation();
        toggle(jobId);
      }}
    >
      <span aria-hidden="true">{saved ? "★" : "☆"}</span>
      {size === "lg" && (
        <span className="bookmarkBtn__label">
          {saved ? "저장됨" : "저장"}
        </span>
      )}
    </button>
  );
}
