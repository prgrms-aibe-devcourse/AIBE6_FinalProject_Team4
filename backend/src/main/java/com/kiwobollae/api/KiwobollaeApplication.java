package com.kiwobollae.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing // 생성일, 수정일 자동화
@SpringBootApplication
public class KiwobollaeApplication {

	public static void main(String[] args) {
		SpringApplication.run(KiwobollaeApplication.class, args);
	}

}
