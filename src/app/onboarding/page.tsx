"use client";

// ============================================================================
// 온보딩 / 내 조건
// ----------------------------------------------------------------------------
// 직무(개발직군)·지역·경력·키워드 입력 → PUT /api/me/preferences.
// 부담 없이: 스킵 가능, 나중에 언제든 수정. 입력은 피드의 초기 필터 프리셋이 된다.
// ============================================================================

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import type { ExperienceLevel, UserPreference } from "@/types/contract";
import {
  DEV_ROLE_OPTIONS,
  EXPERIENCE_OPTIONS,
  LOCATION_OPTIONS,
} from "@/types/contract";
import { fetchPreferences, savePreferences } from "@/lib/api";
import { ErrorState } from "@/components/states";

export default function OnboardingPage() {
  const router = useRouter();

  const [roles, setRoles] = useState<string[]>([]);
  const [locations, setLocations] = useState<string[]>([]);
  const [experience, setExperience] = useState<ExperienceLevel>("NEW");
  const [keywords, setKeywords] = useState<string[]>([]);
  const [keywordInput, setKeywordInput] = useState("");

  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);

  // 기존 조건 프리필(수정 진입 대응)
  useEffect(() => {
    let alive = true;
    fetchPreferences()
      .then((p) => {
        if (!alive) return;
        setRoles(p.roles);
        setLocations(p.locations);
        setExperience(p.experience);
        setKeywords(p.keywords);
      })
      .catch(() => alive && setLoadError(true))
      .finally(() => alive && setLoading(false));
    return () => {
      alive = false;
    };
  }, []);

  const toggle = (list: string[], v: string) =>
    list.includes(v) ? list.filter((x) => x !== v) : [...list, v];

  const addKeyword = () => {
    const raw = keywordInput.trim();
    if (!raw) return;
    // 콤마 다중 입력 허용
    const parts = raw
      .split(",")
      .map((s) => s.trim())
      .filter(Boolean);
    setKeywords((prev) => Array.from(new Set([...prev, ...parts])));
    setKeywordInput("");
  };

  const buildBody = (): UserPreference => ({
    roles,
    locations,
    experience,
    keywords,
  });

  const save = async (thenGoFeed: boolean) => {
    setSaving(true);
    setSaveError(null);
    try {
      await savePreferences(buildBody());
      if (thenGoFeed) router.push("/jobs");
    } catch {
      setSaveError("조건을 저장하지 못했어요. 잠시 후 다시 시도해 주세요.");
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <div className="page">
        <div className="detailSkeleton">
          <div className="sk sk--line sk--w40" />
          <div className="sk sk--block" />
        </div>
      </div>
    );
  }

  if (loadError) {
    return (
      <div className="page">
        <ErrorState
          message="기존 조건을 불러오지 못했어요. 새로 입력해도 돼요."
          action={
            <button
              className="btn btn--primary"
              onClick={() => setLoadError(false)}
            >
              새로 입력하기
            </button>
          }
        />
      </div>
    );
  }

  return (
    <div className="page onboarding">
      <div className="pageHead">
        <div>
          <h1 className="pageHead__title">어떤 공고를 볼까요?</h1>
          <p className="pageHead__sub">
            입력하면 피드가 내 조건으로 맞춰져요. 언제든 수정할 수 있어요.
          </p>
        </div>
      </div>

      {/* 직무 */}
      <section className="formSection">
        <h2 className="formSection__title">
          관심 직무 <span className="formSection__hint">개발직군 · 복수 선택</span>
        </h2>
        <div className="chips">
          {DEV_ROLE_OPTIONS.map((o) => {
            const on = roles.includes(o.value);
            return (
              <button
                key={o.value}
                type="button"
                className={`chip${on ? " chip--on" : ""}`}
                aria-pressed={on}
                onClick={() => setRoles((r) => toggle(r, o.value))}
              >
                {o.label}
              </button>
            );
          })}
        </div>
      </section>

      {/* 지역 */}
      <section className="formSection">
        <h2 className="formSection__title">
          희망 지역 <span className="formSection__hint">복수 선택</span>
        </h2>
        <div className="chips">
          {LOCATION_OPTIONS.map((o) => {
            const on = locations.includes(o.value);
            return (
              <button
                key={o.value}
                type="button"
                className={`chip${on ? " chip--on" : ""}`}
                aria-pressed={on}
                onClick={() => setLocations((l) => toggle(l, o.value))}
              >
                {o.label}
              </button>
            );
          })}
        </div>
      </section>

      {/* 경력 */}
      <section className="formSection">
        <h2 className="formSection__title">경력</h2>
        <div className="chips">
          {EXPERIENCE_OPTIONS.map((o) => {
            const on = experience === o.value;
            return (
              <button
                key={o.value}
                type="button"
                className={`chip${on ? " chip--on" : ""}`}
                aria-pressed={on}
                onClick={() => setExperience(o.value)}
              >
                {o.label}
              </button>
            );
          })}
        </div>
      </section>

      {/* 키워드 */}
      <section className="formSection">
        <h2 className="formSection__title">
          키워드 <span className="formSection__hint">선택 · 예: TypeScript</span>
        </h2>
        <div className="keywordInput">
          <input
            className="input"
            type="text"
            placeholder="키워드 입력 후 Enter"
            value={keywordInput}
            onChange={(e) => setKeywordInput(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter") {
                e.preventDefault();
                addKeyword();
              }
            }}
          />
          <button type="button" className="btn btn--outline btn--sm" onClick={addKeyword}>
            추가
          </button>
        </div>
        {keywords.length > 0 && (
          <div className="chips chips--tags">
            {keywords.map((k) => (
              <span key={k} className="tag">
                {k}
                <button
                  type="button"
                  className="tag__remove"
                  aria-label={`${k} 삭제`}
                  onClick={() =>
                    setKeywords((prev) => prev.filter((x) => x !== k))
                  }
                >
                  ×
                </button>
              </span>
            ))}
          </div>
        )}
      </section>

      {saveError && (
        <p className="formError" role="alert">
          {saveError}
        </p>
      )}

      <div className="onboarding__actions">
        <button
          type="button"
          className="btn btn--primary btn--lg"
          disabled={saving}
          onClick={() => save(true)}
        >
          {saving ? "저장 중…" : "저장하고 공고 보기"}
        </button>
        <button
          type="button"
          className="btn btn--ghost"
          disabled={saving}
          onClick={() => router.push("/jobs")}
        >
          건너뛰기
        </button>
      </div>
    </div>
  );
}
