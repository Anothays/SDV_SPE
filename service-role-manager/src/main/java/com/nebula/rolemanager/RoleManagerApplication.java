package com.nebula.rolemanager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RoleManagerApplication {

	public static void main(String[] args) {
		SpringApplication.run(RoleManagerApplication.class, args);
	}

}
