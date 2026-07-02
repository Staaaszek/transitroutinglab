package pl.edu.pitp.transit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TransitRoutingLabApplication {
    public static void main(String[] args) {
        SpringApplication.run(TransitRoutingLabApplication.class, args);
    }
}
