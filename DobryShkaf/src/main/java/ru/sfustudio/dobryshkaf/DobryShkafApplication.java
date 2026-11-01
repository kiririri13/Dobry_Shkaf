package ru.sfustudio.dobryshkaf;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class DobryShkafApplication {

    public static void main(String[] args) {
        SpringApplication.run(DobryShkafApplication.class, args);
    }

}
