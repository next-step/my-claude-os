"""에러 규격: 모든 실패 응답에 errorCode 필드를 싣는다."""

from __future__ import annotations

from fastapi import FastAPI, Request, status
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

# errorCode 상수
DUPLICATE_USERNAME = "DUPLICATE_USERNAME"
INVALID_CREDENTIALS = "INVALID_CREDENTIALS"
VALIDATION_ERROR = "VALIDATION_ERROR"


class AppError(Exception):
    """애플리케이션 레벨 오류. error_code/status/message 를 함께 전달한다."""

    def __init__(self, error_code: str, status_code: int, message: str) -> None:
        super().__init__(message)
        self.error_code = error_code
        self.status_code = status_code
        self.message = message


def duplicate_username() -> AppError:
    return AppError(
        DUPLICATE_USERNAME,
        status.HTTP_409_CONFLICT,
        "이미 존재하는 아이디입니다.",
    )


def invalid_credentials() -> AppError:
    # 아이디 없음/비밀번호 불일치를 구분하지 않고 동일 응답.
    return AppError(
        INVALID_CREDENTIALS,
        status.HTTP_401_UNAUTHORIZED,
        "아이디 또는 비밀번호가 올바르지 않습니다.",
    )


async def app_error_handler(request: Request, exc: AppError) -> JSONResponse:
    return JSONResponse(
        status_code=exc.status_code,
        content={"errorCode": exc.error_code, "message": exc.message},
    )


async def validation_error_handler(
    request: Request, exc: RequestValidationError
) -> JSONResponse:
    """Pydantic 검증 실패를 errorCode:VALIDATION_ERROR 로 정규화한다."""

    # 첫 번째 에러 메시지를 사용자 안내로 노출한다.
    message = "입력값이 올바르지 않습니다."
    errors = exc.errors()
    if errors:
        first = errors[0]
        message = str(first.get("msg", message))
    return JSONResponse(
        status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
        content={"errorCode": VALIDATION_ERROR, "message": message},
    )


def register_error_handlers(app: FastAPI) -> None:
    app.add_exception_handler(AppError, app_error_handler)
    app.add_exception_handler(RequestValidationError, validation_error_handler)
