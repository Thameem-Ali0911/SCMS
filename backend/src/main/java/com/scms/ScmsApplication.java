package com.scms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * ScmsApplication — Spring Boot entry point.
 *
 * @SpringBootApplication combines:
 *   @Configuration      → this class defines Spring beans
 *   @EnableAutoConfiguration → Spring Boot wires beans based on classpath
 *   @ComponentScan      → scans com.scms.* for @Service, @Repository, @Controller …
 *
 * Running `mvn spring-boot:run` starts an embedded Tomcat on port 8080,
 * connects to MySQL, runs Hibernate DDL, and registers all REST endpoints.
 * Zero XML. Zero standalone server deployment.
 */
@SpringBootApplication
public class ScmsApplication {
    public static void main(String[] args) {
        SpringApplication.run(ScmsApplication.class, args);
    }
}
