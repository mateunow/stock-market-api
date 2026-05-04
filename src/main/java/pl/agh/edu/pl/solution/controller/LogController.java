package pl.agh.edu.pl.solution.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.agh.edu.pl.solution.model.LogEntry;
import pl.agh.edu.pl.solution.service.StockMarketService;

import java.util.List;
import java.util.Map;

@RestController
public class LogController {

    private final StockMarketService service;

    public LogController(StockMarketService service) {
        this.service = service;
    }

    @GetMapping("/log")
    public ResponseEntity<Map<String, List<LogEntry>>> getLog() {
        return ResponseEntity.ok(Map.of("log", service.getLog()));
    }
}
