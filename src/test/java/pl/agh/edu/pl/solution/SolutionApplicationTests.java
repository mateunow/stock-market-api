package pl.agh.edu.pl.solution;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import pl.agh.edu.pl.solution.controller.ChaosController;
import pl.agh.edu.pl.solution.controller.LogController;
import pl.agh.edu.pl.solution.controller.StockController;
import pl.agh.edu.pl.solution.service.StockMarketService;

@WebMvcTest(controllers = {StockController.class, LogController.class, ChaosController.class})
class SolutionApplicationTests {

    @MockBean
    StockMarketService service;

    @Test
    void contextLoads() {
    }
}
