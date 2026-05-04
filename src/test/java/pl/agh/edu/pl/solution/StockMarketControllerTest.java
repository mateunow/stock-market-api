package pl.agh.edu.pl.solution;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.agh.edu.pl.solution.model.LogEntry;
import pl.agh.edu.pl.solution.model.StockEntry;
import pl.agh.edu.pl.solution.service.StockMarketService;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class StockMarketControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    StockMarketService service;

    @BeforeEach
    void setUp() {
        reset(service);
    }

    // ── POST /wallets/{wallet_id}/stocks/{stock_name} ─────────────────────────

    @Test
    void buy_ok_returns200() throws Exception {
        when(service.buy("w1", "AAPL")).thenReturn(StockMarketService.BuyResult.OK);

        mockMvc.perform(post("/wallets/w1/stocks/AAPL")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"buy\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void buy_stockNotFound_returns404() throws Exception {
        when(service.buy("w1", "UNKNOWN")).thenReturn(StockMarketService.BuyResult.STOCK_NOT_FOUND);

        mockMvc.perform(post("/wallets/w1/stocks/UNKNOWN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"buy\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void buy_insufficientBankStock_returns400() throws Exception {
        when(service.buy("w1", "AAPL")).thenReturn(StockMarketService.BuyResult.INSUFFICIENT_BANK_STOCK);

        mockMvc.perform(post("/wallets/w1/stocks/AAPL")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"buy\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sell_ok_returns200() throws Exception {
        when(service.sell("w1", "AAPL")).thenReturn(StockMarketService.SellResult.OK);

        mockMvc.perform(post("/wallets/w1/stocks/AAPL")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"sell\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void sell_stockNotFound_returns404() throws Exception {
        when(service.sell("w1", "UNKNOWN")).thenReturn(StockMarketService.SellResult.STOCK_NOT_FOUND);

        mockMvc.perform(post("/wallets/w1/stocks/UNKNOWN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"sell\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void sell_insufficientWalletStock_returns400() throws Exception {
        when(service.sell("w1", "AAPL")).thenReturn(StockMarketService.SellResult.INSUFFICIENT_WALLET_STOCK);

        mockMvc.perform(post("/wallets/w1/stocks/AAPL")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"sell\"}"))
                .andExpect(status().isBadRequest());
    }

    // ── GET /wallets/{wallet_id} ──────────────────────────────────────────────

    @Test
    void getWallet_returnsStocks() throws Exception {
        when(service.getWalletStocks("w1"))
                .thenReturn(List.of(new StockEntry("AAPL", 3)));

        mockMvc.perform(get("/wallets/w1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("w1"))
                .andExpect(jsonPath("$.stocks[0].name").value("AAPL"))
                .andExpect(jsonPath("$.stocks[0].quantity").value(3));
    }

    // ── GET /wallets/{wallet_id}/stocks/{stock_name} ──────────────────────────

    @Test
    void getWalletStock_returnsQuantity() throws Exception {
        when(service.stockExistsInBank("AAPL")).thenReturn(true);
        when(service.getWalletStockQuantity("w1", "AAPL")).thenReturn(7L);

        mockMvc.perform(get("/wallets/w1/stocks/AAPL"))
                .andExpect(status().isOk())
                .andExpect(content().string("7"));
    }

    @Test
    void getWalletStock_unknownStock_returns404() throws Exception {
        when(service.stockExistsInBank("UNKNOWN")).thenReturn(false);

        mockMvc.perform(get("/wallets/w1/stocks/UNKNOWN"))
                .andExpect(status().isNotFound());
    }

    // ── GET /stocks ───────────────────────────────────────────────────────────

    @Test
    void getStocks_returnsBankState() throws Exception {
        when(service.getBankStocks())
                .thenReturn(List.of(new StockEntry("GOOG", 10)));

        mockMvc.perform(get("/stocks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stocks[0].name").value("GOOG"))
                .andExpect(jsonPath("$.stocks[0].quantity").value(10));
    }

    // ── POST /stocks ──────────────────────────────────────────────────────────

    @Test
    void setStocks_returns200() throws Exception {
        mockMvc.perform(post("/stocks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stocks\":[{\"name\":\"AAPL\",\"quantity\":100}]}"))
                .andExpect(status().isOk());

        verify(service).setBankStocks(anyList());
    }

    // ── GET /log ──────────────────────────────────────────────────────────────

    @Test
    void getLog_returnsAuditLog() throws Exception {
        when(service.getLog())
                .thenReturn(List.of(new LogEntry("buy", "w1", "AAPL")));

        mockMvc.perform(get("/log"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.log[0].type").value("buy"))
                .andExpect(jsonPath("$.log[0].wallet_id").value("w1"))
                .andExpect(jsonPath("$.log[0].stock_name").value("AAPL"));
    }
}
