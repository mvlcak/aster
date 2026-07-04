package dev.mvlcak.aster;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AsterApplication {

	public static void main(String[] args) {
		SpringApplication.run(AsterApplication.class, args);
	}

}
