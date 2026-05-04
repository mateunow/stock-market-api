package pl.agh.edu.pl.solution;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=6370" // intentionally unreachable - context loads lazily
})
class SolutionApplicationTests {

    @Test
    void contextLoads() {
        // Verifies that the Spring context assembles correctly
    }
}
