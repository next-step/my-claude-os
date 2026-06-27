"""테스트 픽스처: 테스트용 JWT_SECRET 주입 + 격리된 인메모리 SQLite + TestClient.

환경변수는 app 모듈을 import 하기 전에 설정해야 config 가 안전하게 로드된다.
"""

from __future__ import annotations

import os

# app 패키지 import 이전에 테스트용 환경변수를 주입한다(시크릿 하드코딩 금지 준수).
os.environ.setdefault("JWT_SECRET", "test-secret-do-not-use-in-prod")
os.environ.setdefault("JWT_ALGORITHM", "HS256")
os.environ.setdefault("JWT_EXPIRE_MIN", "60")

from typing import Iterator  # noqa: E402

import pytest  # noqa: E402
from fastapi.testclient import TestClient  # noqa: E402
from sqlalchemy.pool import StaticPool  # noqa: E402

import app.database as database  # noqa: E402


@pytest.fixture()
def client() -> Iterator[TestClient]:
    """매 테스트마다 격리된 인메모리 SQLite 와 TestClient 를 제공한다."""

    from sqlalchemy import create_engine
    from sqlalchemy.orm import sessionmaker

    # 인메모리 SQLite 를 단일 커넥션(StaticPool)으로 공유해 스키마/데이터가 유지되게 한다.
    engine = create_engine(
        "sqlite://",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
        future=True,
    )
    TestingSessionLocal = sessionmaker(
        bind=engine, autoflush=False, autocommit=False, future=True
    )

    # 전역 엔진/세션 팩토리를 테스트 엔진으로 교체한다.
    database._engine = engine
    database._SessionLocal = TestingSessionLocal

    # 모델 매핑 등록 후 테이블 생성.
    from app import models  # noqa: F401

    database.Base.metadata.create_all(bind=engine)

    # lifespan 의 create_all 은 같은 테스트 엔진을 사용하므로 안전하다.
    from app.main import create_app

    test_app = create_app()
    with TestClient(test_app) as c:
        yield c

    database.Base.metadata.drop_all(bind=engine)
    engine.dispose()
    database._engine = None
    database._SessionLocal = None
