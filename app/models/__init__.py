"""[Model 계층] ORM 엔티티 패키지.

User 를 패키지 레벨에서 노출해 기존 `from app.models import User` 경로를 유지한다.
"""

from __future__ import annotations

from app.models.user import User

__all__ = ["User"]
