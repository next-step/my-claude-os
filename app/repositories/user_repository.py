"""사용자 저장소 [Repository 계층]: DB 접근만 담당한다.

SQLAlchemy 세션은 오직 이 계층에서만 다룬다. 비즈니스 규칙(중복 판정 등)은
상위 service 계층의 책임이며 여기서는 순수 조회/저장만 수행한다.
"""

from __future__ import annotations

from typing import Optional

from sqlalchemy.orm import Session

from app.models import User


class UserRepository:
    """User 엔티티에 대한 DB 접근을 캡슐화한다."""

    def __init__(self, db: Session) -> None:
        self._db = db

    def get_by_username(self, username: str) -> Optional[User]:
        """username(대소문자 구분)으로 사용자를 조회한다. 없으면 None."""

        return self._db.query(User).filter(User.username == username).first()

    def create(
        self, username: str, name: str, password_hash: str, salt: str
    ) -> User:
        """사용자를 저장하고 PK 가 채워진 User 를 반환한다."""

        user = User(
            username=username,
            name=name,
            password_hash=password_hash,
            salt=salt,
        )
        self._db.add(user)
        self._db.commit()
        self._db.refresh(user)
        return user
