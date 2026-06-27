"""인증 컨트롤러 [Controller 계층]: POST /signup, POST /login.

HTTP 입출력만 담당한다. 요청 스키마로 받아 service 를 호출하고, 결과를
응답 스키마로 변환한다. 비즈니스 로직·DB 접근은 이 계층에 없다.
"""

from __future__ import annotations

from fastapi import APIRouter, Depends, status
from sqlalchemy.orm import Session

from app.config import get_settings
from app.database import get_db
from app.repositories.user_repository import UserRepository
from app.schemas.auth import (
    LoginRequest,
    SignupRequest,
    SignupResponse,
    TokenResponse,
)
from app.services.auth_service import AuthService

router = APIRouter(tags=["auth"])


def get_auth_service(db: Session = Depends(get_db)) -> AuthService:
    """요청 단위 세션으로 repository·service 를 조립한다(의존성 주입)."""

    return AuthService(UserRepository(db))


@router.post(
    "/signup",
    response_model=SignupResponse,
    status_code=status.HTTP_201_CREATED,
)
def signup(
    payload: SignupRequest,
    service: AuthService = Depends(get_auth_service),
) -> SignupResponse:
    user = service.signup(payload.username, payload.password, payload.name)
    return SignupResponse(userId=user.id, username=user.username, name=user.name)


@router.post("/login", response_model=TokenResponse)
def login(
    payload: LoginRequest,
    service: AuthService = Depends(get_auth_service),
) -> TokenResponse:
    token = service.login(payload.username, payload.password)
    expires_in = get_settings().jwt_expire_min * 60
    return TokenResponse(accessToken=token, tokenType="bearer", expiresIn=expires_in)
