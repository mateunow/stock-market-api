package pl.agh.edu.pl.solution.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.agh.edu.pl.solution.model.StockEntry;
import pl.agh.edu.pl.solution.service.StockMarketService;

import java.util.List;
import java.util.Map;

@RestController
public class StockController {

    private final StockMarketService service;

    public StockController(StockMarketService service) {
        this.service = service;
    }

    // ── POST /wallets/{wallet_id}/stocks/{stock_name} ─────────────────────────

    record TradeRequest(String type) {}

    @PostMapping("/wallets/{wallet_id}/stocks/{stock_name}")
    public ResponseEntity<Void> trade(
            @PathVariable("wallet_id") String walletId,
            @PathVariable("stock_name") String stockName,
            @RequestBody TradeRequest body) {

        if ("buy".equalsIgnoreCase(body.type())) {
            return switch (service.buy(walletId, stockName)) {
                case OK -> ResponseEntity.ok().build();
                case STOCK_NOT_FOUND -> ResponseEntity.notFound().build();
                case INSUFFICIENT_BANK_STOCK -> ResponseEntity.badRequest().build();
            };
        } else if ("sell".equalsIgnoreCase(body.type())) {
            return switch (service.sell(walletId, stockName)) {
                case OK -> ResponseEntity.ok().build();
                case STOCK_NOT_FOUND -> ResponseEntity.notFound().build();
                case INSUFFICIENT_WALLET_STOCK -> ResponseEntity.badRequest().build();
            };
        }
        return ResponseEntity.badRequest().build();
    }

    // ── GET /wallets/{wallet_id} ──────────────────────────────────────────────

    @GetMapping("/wallets/{wallet_id}")
    public ResponseEntity<Map<String, Object>> getWallet(
            @PathVariable("wallet_id") String walletId) {

        List<StockEntry> stocks = service.getWalletStocks(walletId);
        return ResponseEntity.ok(Map.of("id", walletId, "stocks", stocks));
    }

    // ── GET /wallets/{wallet_id}/stocks/{stock_name} ──────────────────────────

    @GetMapping("/wallets/{wallet_id}/stocks/{stock_name}")
    public ResponseEntity<Long> getWalletStock(
            @PathVariable("wallet_id") String walletId,
            @PathVariable("stock_name") String stockName) {

        return ResponseEntity.ok(service.getWalletStockQuantity(walletId, stockName));
    }

    // ── GET /stocks ───────────────────────────────────────────────────────────

    @GetMapping("/stocks")
    public ResponseEntity<Map<String, Object>> getStocks() {
        return ResponseEntity.ok(Map.of("stocks", service.getBankStocks()));
    }

    // ── POST /stocks ──────────────────────────────────────────────────────────

    record SetStocksRequest(List<StockEntry> stocks) {}

    @PostMapping("/stocks")
    public ResponseEntity<Void> setStocks(@RequestBody SetStocksRequest body) {
        service.setBankStocks(body.stocks());
        return ResponseEntity.ok().build();
    }
}
