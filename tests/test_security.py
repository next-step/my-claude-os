"""단위 테스트: 솔트/해시/검증/토큰."""

from __future__ import annotations

import os
from datetime import datetime, timezone

os.environ.setdefault("JWT_SECRET", "test-secret-do-not-use-in-prod")

from jose import jwt  # noqa: E402

from app.config import get_settings  # noqa: E402
from app.security import password as security  # noqa: E402
from app.security.token import create_access_token  # noqa: E402


def test_make_salt_is_unique_and_64_hex_chars():
    s1 = security.make_salt()
    s2 = security.make_salt()
    assert s1 != s2
    assert len(s1) == 64
    int(s1, 16)  # hex 파싱 가능해야 함


def test_same_pw_and_salt_gives_same_hash():
    pw, salt = "abcd1234", security.make_salt()
    assert security.hash_password(pw, salt) == security.hash_password(pw, salt)
    assert len(security.hash_password(pw, salt)) == 128  # sha512 hex


def test_different_salt_gives_different_hash():
    pw = "abcd1234"
    h1 = security.hash_password(pw, security.make_salt())
    h2 = security.hash_password(pw, security.make_salt())
    assert h1 != h2


def test_verify_password_true_for_correct_false_for_wrong():
    pw, salt = "abcd1234", security.make_salt()
    digest = security.hash_password(pw, salt)
    assert security.verify_password(pw, salt, digest) is True
    assert security.verify_password("wrongpw9", salt, digest) is False


def test_create_access_token_payload_and_expiry():
    settings = get_settings()
    token = create_access_token(42)
    decoded = jwt.decode(
        token, settings.jwt_secret, algorithms=[settings.jwt_algorithm]
    )
    assert decoded["sub"] == "42"
    now = datetime.now(timezone.utc).timestamp()
    # 만료가 약 60분 후(여유 있게 55~65분 사이)인지 확인
    assert 55 * 60 < (decoded["exp"] - now) < 65 * 60


def test_token_decode_fails_with_wrong_secret():
    from jose.exceptions import JWTError

    token = create_access_token(1)
    try:
        jwt.decode(token, "wrong-secret", algorithms=["HS256"])
        assert False, "잘못된 시크릿으로 디코드가 성공하면 안 된다"
    except JWTError:
        pass
