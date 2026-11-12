package com.iitsm.iitsm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling ;

@SpringBootApplication
@EnableScheduling
public class IitsmApplication {

	public static void main(String[] args) {
		SpringApplication.run(IitsmApplication.class, args);
	}

}
