// 루트 진입 → 공고 피드로. (M1 은 단일 로컬 사용자, 별도 랜딩 불필요)
import { redirect } from "next/navigation";

export default function Home() {
  redirect("/jobs");
}
