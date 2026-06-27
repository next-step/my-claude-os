"""인증 비즈니스 로직 [Service 계층].

가입/로그인 규칙을 담는다: 중복 판정, 솔트·해시 생성, 비밀번호 검증, 토큰 발급 흐름.
DB 접근은 repository 에, 해싱·토큰은 security 에 위임하고, 도메인 예외(AppError)는
이 계층에서 발생시킨다. HTTP·세션은 알지 못한다(상·하위 계층 비결합).
"""

from __future__ import annotations

from app import errors
from app.models import User
from app.repositories.user_repository import UserRepository
from app.security import password as password_sec
from app.security.token import create_access_token


class AuthService:
    """가입·로그인 유스케이스를 처리한다."""

    def __init__(self, repository: UserRepository) -> None:
        self._repository = repository

    def signup(self, username: str, raw_password: str, name: str) -> User:
        """중복을 판정하고 솔트·해시를 생성해 사용자를 저장한다.

        username 은 이미 schema 단계에서 trim 된 값이 들어온다. 중복이면
        DUPLICATE_USERNAME(AppError)을 발생시킨다.
        """

        existing = self._repository.get_by_username(username)
        if existing is not None:
            raise errors.duplicate_username()

        salt = password_sec.make_salt()
        password_hash = password_sec.hash_password(raw_password, salt)
        return self._repository.create(
            username=username,
            name=name,
            password_hash=password_hash,
            salt=salt,
        )

    def login(self, username: str, raw_password: str) -> str:
        """자격 검증 후 access token 을 발급한다.

        아이디 없음/비밀번호 불일치를 구분하지 않고 동일하게
        INVALID_CREDENTIALS(AppError)를 발생시킨다(정보 누출 방지).
        """

        user = self._repository.get_by_username(username)
        if user is None or not password_sec.verify_password(
            raw_password, user.salt, user.password_hash
        ):
            raise errors.invalid_credentials()

        return create_access_token(user.id)
