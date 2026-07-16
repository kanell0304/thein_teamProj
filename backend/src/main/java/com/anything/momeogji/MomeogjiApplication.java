package com.anything.momeogji;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.anything")
public class MomeogjiApplication {

	public static void main(String[] args) {
		SpringApplication.run(MomeogjiApplication.class, args);
	}

}
