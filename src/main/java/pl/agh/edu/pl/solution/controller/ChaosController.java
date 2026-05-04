package pl.agh.edu.pl.solution.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ChaosController {

    @PostMapping("/chaos")
    public void chaos() {
        // Kill this JVM instance — other instances behind the load balancer keep running
        Runtime.getRuntime().halt(1);
    }
}
