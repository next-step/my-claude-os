"""SQLAlchemy 엔진/세션/Base 및 get_db 의존성.

엔진 생성을 lazy 하게 처리해, 테스트가 환경변수(DATABASE_URL/JWT_SECRET)를
먼저 주입한 뒤 엔진을 초기화할 수 있게 한다.
"""

from __future__ import annotations

from typing import Iterator, Optional

from sqlalchemy import create_engine
from sqlalchemy.engine import Engine
from sqlalchemy.orm import Session, declarative_base, sessionmaker

from app.config import get_settings

Base = declarative_base()

_engine: Optional[Engine] = None
_SessionLocal: Optional[sessionmaker] = None


def init_engine(database_url: Optional[str] = None) -> Engine:
    """엔진과 세션 팩토리를 초기화한다.

    SQLite 의 경우 다중 스레드(테스트 클라이언트) 접근을 위해
    check_same_thread=False 를 설정한다.
    """

    global _engine, _SessionLocal
    if database_url is None:
        database_url = get_settings().database_url

    connect_args = {}
    if database_url.startswith("sqlite"):
        connect_args = {"check_same_thread": False}

    _engine = create_engine(database_url, connect_args=connect_args, future=True)
    _SessionLocal = sessionmaker(
        bind=_engine, autoflush=False, autocommit=False, future=True
    )
    return _engine


def get_engine() -> Engine:
    """초기화된 엔진을 반환한다. 없으면 설정 기반으로 초기화한다."""

    if _engine is None:
        init_engine()
    assert _engine is not None
    return _engine


def create_all() -> None:
    """모든 테이블을 생성한다(startup 시 호출)."""

    # models 가 Base 에 매핑을 등록하도록 import 한다.
    from app import models  # noqa: F401

    Base.metadata.create_all(bind=get_engine())


def get_db() -> Iterator[Session]:
    """요청 단위 DB 세션을 제공하는 FastAPI 의존성."""

    if _SessionLocal is None:
        init_engine()
    assert _SessionLocal is not None
    db = _SessionLocal()
    try:
        yield db
    finally:
        db.close()
