"""비밀번호 보안 순수 로직: 솔트 생성, SHA-512 해싱/검증 [Security 계층].

FastAPI/DB 의존성 없이 순수 함수로 두어 단위 테스트가 쉽게 한다.
추후 bcrypt/Argon2 로 교체할 때 hash_password/verify_password 만 바꾸면 된다.
"""

from __future__ import annotations

import hashlib
import secrets

SALT_BYTES = 32  # token_hex(32) -> 64자 hex 문자열


def make_salt() -> str:
    """사용자별 랜덤 솔트(64자 hex)를 생성한다."""

    return secrets.token_hex(SALT_BYTES)


def hash_password(password: str, salt: str) -> str:
    """salt + password 를 SHA-512 로 해싱해 128자 hex digest 를 반환한다."""

    digest = hashlib.sha512((salt + password).encode("utf-8")).hexdigest()
    return digest


def verify_password(password: str, salt: str, password_hash: str) -> bool:
    """동일 방식으로 재계산한 해시를 상수시간 비교한다."""

    candidate = hash_password(password, salt)
    return secrets.compare_digest(candidate, password_hash)
