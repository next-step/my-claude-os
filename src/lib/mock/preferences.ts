// Mock UserPreference — 온보딩/피드 프리셋 화면 unblock 용.
// 실 API(GET/PUT /api/me/preferences) 교체 전까지 프론트가 참조.
import type { UserPreference } from "@/types/contract";

export const MOCK_PREFERENCE: UserPreference = {
  roles: ["backend", "frontend"],
  locations: ["서울", "경기"],
  experience: "NEW",
  keywords: ["TypeScript"],
};
