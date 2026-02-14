package com.github.rrousso.erik_lite;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import com.github.rrousso.erik_lite.controllers.ConsoleRunner;

@SpringBootApplication
public class ErikLiteApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(ErikLiteApplication.class, args);

        ConsoleRunner consoleRunner = context.getBean(ConsoleRunner.class);
        consoleRunner.run();

        context.close();
    }
}