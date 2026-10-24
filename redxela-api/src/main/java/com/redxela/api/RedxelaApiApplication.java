package com.redxela.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@ConfigurationPropertiesScan
@SpringBootApplication
public class RedxelaApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(RedxelaApiApplication.class, args);
	}

}
