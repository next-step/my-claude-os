// Prisma Client 싱글턴 (Next.js dev HMR 시 커넥션 누수 방지).
// M1 선행작업에서는 아직 API 가 DB 를 직접 읽지 않지만(mock 사용),
// seed 스크립트와 이후 실 API 교체 때 이 싱글턴을 공유한다.
import { PrismaClient } from "@prisma/client";

const globalForPrisma = globalThis as unknown as { prisma?: PrismaClient };

export const prisma = globalForPrisma.prisma ?? new PrismaClient();

if (process.env.NODE_ENV !== "production") {
  globalForPrisma.prisma = prisma;
}
