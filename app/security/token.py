"""JWT 토큰 발급 순수 로직 [Security 계층].

HS256, sub=user_id(문자열), 만료 60분(설정 기반). FastAPI/DB 의존성 없음.
"""

from __future__ import annotations

from datetime import datetime, timedelta, timezone
from typing import Any, Dict

from jose import jwt

from app.config import get_settings


def create_access_token(user_id: int) -> str:
    """user_id 를 sub 로 담는 HS256 JWT 를 발급한다.

    payload 는 PRD 규칙대로 user_id(+만료)만 담는다. sub 는 문자열로 저장한다.
    """

    settings = get_settings()
    now = datetime.now(timezone.utc)
    expire = now + timedelta(minutes=settings.jwt_expire_min)
    payload: Dict[str, Any] = {"sub": str(user_id), "exp": expire}
    token = jwt.encode(payload, settings.jwt_secret, algorithm=settings.jwt_algorithm)
    return token
