package com.translation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.SpringApplication;

import com.translation.agent.TranslationAgent;

public class SpringBootStarter {

	private static final Logger logger = LogManager.getLogger(SpringBootStarter.class);
	
	public static void main(String[] args) {
		logger.trace("Enter Spring Boot Starter");
		logger.info("Spring Boot Starter");
		SpringApplication.run(TranslationAgent.class, args);
	}
}
