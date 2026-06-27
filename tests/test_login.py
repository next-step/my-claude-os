"""통합 테스트: /login 성공·실패(아이디없음/비번불일치 동일응답)."""

from __future__ import annotations

from jose import jwt

from app.config import get_settings


def _signup(client, username="dave", password="abcd1234", name="데이브"):
    return client.post(
        "/signup",
        json={"username": username, "password": password, "name": name},
    )


def test_login_success_returns_token(client):
    signup_resp = _signup(client)
    user_id = signup_resp.json()["userId"]

    resp = client.post("/login", json={"username": "dave", "password": "abcd1234"})
    assert resp.status_code == 200
    body = resp.json()
    assert body["tokenType"] == "bearer"
    assert body["expiresIn"] == 3600

    settings = get_settings()
    decoded = jwt.decode(
        body["accessToken"], settings.jwt_secret, algorithms=[settings.jwt_algorithm]
    )
    assert decoded["sub"] == str(user_id)


def test_login_unknown_username_returns_401(client):
    resp = client.post("/login", json={"username": "ghost", "password": "abcd1234"})
    assert resp.status_code == 401
    assert resp.json()["errorCode"] == "INVALID_CREDENTIALS"


def test_login_wrong_password_same_response_as_unknown(client):
    _signup(client)
    wrong_pw = client.post(
        "/login", json={"username": "dave", "password": "wrongpw9"}
    )
    unknown = client.post(
        "/login", json={"username": "ghost", "password": "abcd1234"}
    )
    # 상태코드·응답 바디가 동일해야 한다(정보 누출 방지).
    assert wrong_pw.status_code == unknown.status_code == 401
    assert wrong_pw.json() == unknown.json()
    assert wrong_pw.json()["errorCode"] == "INVALID_CREDENTIALS"
