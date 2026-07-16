package com.anything.momeogji;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

<<<<<<< HEAD
@SpringBootApplication
@ConfigurationPropertiesScan
=======
@SpringBootApplication(scanBasePackages = "com.anything")
>>>>>>> 4fe567c (feat: add code-cardlist*)
public class MomeogjiApplication {

	public static void main(String[] args) {
		SpringApplication.run(MomeogjiApplication.class, args);
	}

}
