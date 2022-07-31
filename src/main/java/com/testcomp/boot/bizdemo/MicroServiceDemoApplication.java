package com.testcomp.boot.bizdemo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@ComponentScan(value = {"com.testcomp"})
@SpringBootApplication
public class MicroServiceDemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(MicroServiceDemoApplication.class, args);
	}
}
