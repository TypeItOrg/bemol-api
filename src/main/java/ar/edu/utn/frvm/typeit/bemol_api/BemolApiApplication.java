package ar.edu.utn.frvm.typeit.bemol_api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class BemolApiApplication {
  public static void main(String[] args) {
    SpringApplication.run(BemolApiApplication.class, args);
  }
}
