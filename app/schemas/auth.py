"""Pydantic 요청/응답 스키마 및 검증 로직 [Schema 계층].

username: trim 후 최대 20자, 공백 불가, 대소문자 구분 유지.
password: 영문 소문자 + 숫자 포함, 8자 이상.
"""

from __future__ import annotations

import re

from pydantic import BaseModel, field_validator

# 비밀번호 규칙: 소문자 1+ , 숫자 1+ , 8자 이상
_LOWER_RE = re.compile(r"[a-z]")
_DIGIT_RE = re.compile(r"\d")


class SignupRequest(BaseModel):
    username: str
    password: str
    name: str

    @field_validator("username")
    @classmethod
    def validate_username(cls, v: str) -> str:
        # 앞뒤 공백 제거 후 판정/저장 (중복 판정 단순화 규칙)
        trimmed = v.strip()
        if not trimmed:
            raise ValueError("아이디는 비어 있을 수 없습니다.")
        if len(trimmed) > 20:
            raise ValueError("아이디는 최대 20자입니다.")
        # 내부 공백 불가 (trim 후에도 공백이 남아 있으면 거부)
        if any(ch.isspace() for ch in trimmed):
            raise ValueError("아이디에는 공백을 포함할 수 없습니다.")
        return trimmed

    @field_validator("name")
    @classmethod
    def validate_name(cls, v: str) -> str:
        trimmed = v.strip()
        if not trimmed:
            raise ValueError("이름은 비어 있을 수 없습니다.")
        return trimmed

    @field_validator("password")
    @classmethod
    def validate_password(cls, v: str) -> str:
        if len(v) < 8:
            raise ValueError("비밀번호는 8자 이상이어야 합니다.")
        if not _LOWER_RE.search(v):
            raise ValueError("비밀번호에는 영문 소문자가 포함되어야 합니다.")
        if not _DIGIT_RE.search(v):
            raise ValueError("비밀번호에는 숫자가 포함되어야 합니다.")
        return v


class SignupResponse(BaseModel):
    userId: int
    username: str
    name: str


class LoginRequest(BaseModel):
    username: str
    password: str

    @field_validator("username")
    @classmethod
    def trim_username(cls, v: str) -> str:
        # 로그인 시에도 동일하게 trim 하여 가입 시 저장된 username 과 맞춘다.
        return v.strip()


class TokenResponse(BaseModel):
    accessToken: str
    tokenType: str = "bearer"
    expiresIn: int


class ErrorResponse(BaseModel):
    errorCode: str
    message: str
