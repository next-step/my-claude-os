"""환경변수 기반 설정 로딩.

JWT_SECRET 은 하드코딩하지 않고 반드시 환경변수로 주입한다.
값이 없으면 기동 단계에서 예외를 던져 안전하게 실패시킨다.
"""

from __future__ import annotations

import os
from typing import Optional


class Settings:
    """애플리케이션 설정값 묶음.

    인스턴스 생성 시점에 환경변수를 읽는다. 이렇게 함수/클래스로 감싸 두면
    테스트에서 환경변수를 주입한 뒤 새 인스턴스를 만들어 격리할 수 있다.
    """

    def __init__(self) -> None:
        secret: Optional[str] = os.getenv("JWT_SECRET")
        if not secret:
            # 하드코딩 금지(PRD). 시크릿이 없으면 기동을 실패시킨다.
            raise RuntimeError(
                "환경변수 JWT_SECRET 이 설정되지 않았습니다. "
                ".env.example 를 참고해 JWT_SECRET 을 주입하세요."
            )
        self.jwt_secret: str = secret
        self.jwt_algorithm: str = os.getenv("JWT_ALGORITHM", "HS256")
        self.jwt_expire_min: int = int(os.getenv("JWT_EXPIRE_MIN", "60"))
        self.database_url: str = os.getenv("DATABASE_URL", "sqlite:///./app.db")


def get_settings() -> Settings:
    """설정 인스턴스를 생성해 반환한다.

    매 호출마다 새로 읽으므로 테스트에서 환경변수를 바꾼 뒤 호출하면 반영된다.
    """

    return Settings()
