plugins {
    `java-library`
}

group = "ai.genesislab"
version = "0.1.0"

java {
    // 로컬에 설치된 Corretto 21(LTS)로 빌드/테스트한다.
    // CONVENTIONS.md는 JDK 23+를 타깃으로 제안하나, 재현 가능한 LTS 빌드를 위해 21을 선택했다.
    // (Corretto 24도 설치돼 있어 toolchain 값만 바꾸면 그대로 동작한다.)
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // 단방향 비밀번호 해싱(BCrypt). 내부 구현에만 사용하므로 implementation 스코프로 추가한다
    // (java-library: public API 시그니처에 BCrypt 타입을 노출하지 않음).
    implementation("at.favre.lib:bcrypt:0.10.2")

    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}
