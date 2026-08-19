plugins {
	java
	id("org.springframework.boot") version "4.1.0"
	id("io.spring.dependency-management") version "1.1.7"
	id("com.diffplug.spotless") version "7.2.1"
}

group = "com.kiwobollae"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation(platform("software.amazon.awssdk:bom:2.29.52"))
	implementation("software.amazon.awssdk:s3")
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-mail")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3")
	implementation("io.jsonwebtoken:jjwt-api:0.12.6")
	compileOnly("org.projectlombok:lombok")
	developmentOnly("org.springframework.boot:spring-boot-devtools")
	runtimeOnly("com.mysql:mysql-connector-j")
	runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
	runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")
	annotationProcessor("org.projectlombok:lombok")
	testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
	testImplementation("org.springframework.boot:spring-boot-starter-security-test")
	testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testCompileOnly("org.projectlombok:lombok")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
	testAnnotationProcessor("org.projectlombok:lombok")
}

tasks.withType<Test> {
	useJUnitPlatform()
}

tasks.named<Test>("test") {
	useJUnitPlatform {
		excludeTags("openai-smoke")
	}
}

tasks.register<Test>("openAiSmokeTest") {
	description = "실제 OpenAI API로 텍스트 구조화 응답을 검증합니다."
	group = "verification"
	testClassesDirs = sourceSets["test"].output.classesDirs
	classpath = sourceSets["test"].runtimeClasspath
	useJUnitPlatform {
		includeTags("openai-smoke")
	}
	doFirst {
		val secretFile = file("src/main/resources/application-secret.yaml")
		val hasEnvironmentVariables =
			listOf("OPENAI_API_KEY", "OPENAI_TEXT_MODEL")
				.all { !System.getenv(it).isNullOrBlank() }
		if (!secretFile.exists() && !hasEnvironmentVariables) {
			throw GradleException(
				"OpenAI 스모크 테스트 설정이 필요합니다: " +
					"src/main/resources/application-secret.yaml 또는 OPENAI_API_KEY/OPENAI_TEXT_MODEL",
			)
		}
	}
}

spotless {
	java {
		googleJavaFormat("1.35.0")
		target(
			"src/main/java/com/kiwobollae/api/ai/**/*.java",
			"src/main/java/com/kiwobollae/api/commerce/gacha/**/*.java",
			"src/main/java/com/kiwobollae/api/content/dto/response/GachaRewardResponse.java",
			"src/main/java/com/kiwobollae/api/global/config/GachaCardInitData.java",
			"src/test/java/com/kiwobollae/api/ai/**/*.java",
			"src/test/java/com/kiwobollae/api/commerce/gacha/**/*.java",
		)
	}
}
