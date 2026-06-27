"""FastAPI 앱 조립: 라우터 등록, 예외 핸들러, startup 시 DB 테이블 생성."""

from __future__ import annotations

from contextlib import asynccontextmanager
from typing import AsyncIterator

from fastapi import FastAPI

from app.controllers import auth_controller
from app.database import create_all
from app.errors import register_error_handlers


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    # startup: 테이블 생성
    create_all()
    yield


def create_app() -> FastAPI:
    app = FastAPI(title="회원가입·로그인 API", lifespan=lifespan)
    register_error_handlers(app)
    app.include_router(auth_controller.router)
    return app


app = create_app()
