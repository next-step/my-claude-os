"""통합 테스트: /signup 성공·중복·검증실패."""

from __future__ import annotations


def test_signup_success(client):
    resp = client.post(
        "/signup",
        json={"username": "alice", "password": "abcd1234", "name": "앨리스"},
    )
    assert resp.status_code == 201
    body = resp.json()
    assert body["username"] == "alice"
    assert body["name"] == "앨리스"
    assert isinstance(body["userId"], int)
    # 비밀번호/솔트/해시 미노출
    assert "password" not in body
    assert "salt" not in body
    assert "password_hash" not in body


def test_signup_duplicate_username_conflict(client):
    payload = {"username": "bob", "password": "abcd1234", "name": "밥"}
    assert client.post("/signup", json=payload).status_code == 201
    resp = client.post("/signup", json=payload)
    assert resp.status_code == 409
    assert resp.json()["errorCode"] == "DUPLICATE_USERNAME"


def test_signup_duplicate_after_trim(client):
    assert (
        client.post(
            "/signup",
            json={"username": "alice", "password": "abcd1234", "name": "앨리스"},
        ).status_code
        == 201
    )
    # 앞뒤 공백만 다른 경우 trim 후 동일 -> 409
    resp = client.post(
        "/signup",
        json={"username": "  alice  ", "password": "abcd1234", "name": "앨리스2"},
    )
    assert resp.status_code == 409
    assert resp.json()["errorCode"] == "DUPLICATE_USERNAME"


def test_signup_case_sensitive_both_succeed(client):
    r1 = client.post(
        "/signup",
        json={"username": "Alice", "password": "abcd1234", "name": "앨리스"},
    )
    r2 = client.post(
        "/signup",
        json={"username": "alice", "password": "abcd1234", "name": "앨리스"},
    )
    assert r1.status_code == 201
    assert r2.status_code == 201


def test_signup_invalid_password_validation_error(client):
    resp = client.post(
        "/signup",
        json={"username": "carol", "password": "short", "name": "캐럴"},
    )
    assert resp.status_code in (400, 422)
    assert resp.json()["errorCode"] == "VALIDATION_ERROR"


def test_signup_username_too_long_validation_error(client):
    resp = client.post(
        "/signup",
        json={"username": "a" * 21, "password": "abcd1234", "name": "롱"},
    )
    assert resp.status_code in (400, 422)
    assert resp.json()["errorCode"] == "VALIDATION_ERROR"
