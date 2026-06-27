"""단위 테스트: username/password validator."""

from __future__ import annotations

import os

os.environ.setdefault("JWT_SECRET", "test-secret-do-not-use-in-prod")

import pytest  # noqa: E402
from pydantic import ValidationError  # noqa: E402

from app.schemas.auth import SignupRequest  # noqa: E402


def _make(**over):
    base = {"username": "alice", "password": "abcd1234", "name": "앨리스"}
    base.update(over)
    return SignupRequest(**base)


def test_username_over_20_chars_rejected():
    with pytest.raises(ValidationError):
        _make(username="a" * 21)


def test_username_with_inner_whitespace_rejected():
    with pytest.raises(ValidationError):
        _make(username="ab cd")


def test_username_is_trimmed():
    req = _make(username="  abc  ")
    assert req.username == "abc"


def test_username_is_case_sensitive_preserved():
    assert _make(username="Abc").username == "Abc"
    assert _make(username="abc").username == "abc"


def test_password_too_short_rejected():
    with pytest.raises(ValidationError):
        _make(password="abc123")


def test_password_without_lowercase_rejected():
    with pytest.raises(ValidationError):
        _make(password="123456789")


def test_password_without_digit_rejected():
    with pytest.raises(ValidationError):
        _make(password="abcdefgh")


def test_valid_password_passes():
    assert _make(password="abcd1234").password == "abcd1234"
