"""User ORM 모델 [Model 계층].

DB 스키마(엔티티)만 정의한다. 비즈니스 로직·세션 접근은 상위 계층(service/repository)이 담당.
"""

from __future__ import annotations

from datetime import datetime, timezone

from sqlalchemy import Column, Integer, String

from app.database import Base


def _utcnow_iso() -> str:
    """현재 시각을 ISO8601 UTC 문자열로 반환한다."""

    return datetime.now(timezone.utc).isoformat()


class User(Base):
    """사용자 테이블.

    비밀번호는 평문으로 저장하지 않고 SHA-512 해시(hex)와 솔트를 별도 컬럼에 저장한다.
    """

    __tablename__ = "users"

    id = Column(Integer, primary_key=True, autoincrement=True)
    # 대소문자 구분 중복 판정을 위해 SQLite 기본 BINARY collation 을 그대로 사용한다.
    username = Column(String, nullable=False, unique=True)
    name = Column(String, nullable=False)
    password_hash = Column(String, nullable=False)  # SHA-512 hex (128자)
    salt = Column(String, nullable=False)  # token_hex(32) -> 64자 hex
    created_at = Column(String, nullable=False, default=_utcnow_iso)
