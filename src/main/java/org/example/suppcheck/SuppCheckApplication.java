package org.example.suppcheck;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Springboot App mit Component scan.
 */

@SpringBootApplication
@ComponentScan(basePackages = {"org.example.suppcheck.controller", "org.example.suppcheck.service",
    "org.example.suppcheck.model", "org.example.suppcheck.repository",
    "org.example.suppcheck.health", "org.example.suppcheck.gymbook"})
public class SuppCheckApplication {

  /**
   * Main Methode um die Spring Boot Anwendung zu starten.
   *
   * @param args Startargumente für die Anwendung
   */
  public static void main(String[] args) {
    SpringApplication.run(SuppCheckApplication.class, args);
  }


}