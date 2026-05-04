package pl.agh.edu.pl.solution.service;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.stereotype.Service;
import pl.agh.edu.pl.solution.model.LogEntry;
import pl.agh.edu.pl.solution.model.StockEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class StockMarketService {

    // Redis key prefixes
    private static final String BANK_KEY = "bank:stocks";          // Hash: stockName -> quantity
    private static final String WALLET_KEY_PREFIX = "wallet:";     // Hash: stockName -> quantity
    private static final String LOG_KEY = "audit:log";             // List of serialized log entries

    private final RedisTemplate<String, Object> redisTemplate;

    public StockMarketService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // ── Bank ──────────────────────────────────────────────────────────────────

    public List<StockEntry> getBankStocks() {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(BANK_KEY);
        return toStockList(entries);
    }

    /**
     * Replaces the entire bank state atomically.
     * Zeros existing stocks then sets new quantities.
     */
    public void setBankStocks(List<StockEntry> stocks) {
        redisTemplate.execute(new SessionCallback<>() {
            @Override
            public Object execute(RedisOperations operations) throws DataAccessException {
                operations.multi();
                operations.delete(BANK_KEY);
                for (StockEntry s : stocks) {
                    operations.opsForHash().put(BANK_KEY, s.name(), String.valueOf(s.quantity()));
                }
                return operations.exec();
            }
        });
    }

    // ── Wallet ────────────────────────────────────────────────────────────────

    public boolean stockExistsInBank(String stockName) {
        return redisTemplate.opsForHash().hasKey(BANK_KEY, stockName);
    }

    public List<StockEntry> getWalletStocks(String walletId) {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(walletKey(walletId));
        return toStockList(entries);
    }

    public Long getWalletStockQuantity(String walletId, String stockName) {
        Object val = redisTemplate.opsForHash().get(walletKey(walletId), stockName);
        return val == null ? 0L : Long.parseLong(val.toString());
    }

    /**
     * Buy: bank → wallet.
     * Returns false if bank has 0 of this stock.
     * Returns null if stock doesn't exist in bank at all.
     */
    public BuyResult buy(String walletId, String stockName) {
        if (!stockExistsInBank(stockName)) {
            return BuyResult.STOCK_NOT_FOUND;
        }

        // Optimistic-loop with WATCH to ensure atomicity across instances
        for (int attempt = 0; attempt < 10; attempt++) {
            redisTemplate.watch(BANK_KEY);
            Object raw = redisTemplate.opsForHash().get(BANK_KEY, stockName);
            long bankQty = raw == null ? 0L : Long.parseLong(raw.toString());

            if (bankQty <= 0) {
                redisTemplate.unwatch();
                return BuyResult.INSUFFICIENT_BANK_STOCK;
            }

            @SuppressWarnings("unchecked")
            List<Object> result = (List<Object>) redisTemplate.execute(new SessionCallback<>() {
                @Override
                public Object execute(RedisOperations operations) throws DataAccessException {
                    operations.multi();
                    operations.opsForHash().increment(BANK_KEY, stockName, -1);
                    operations.opsForHash().increment(walletKey(walletId), stockName, 1);
                    return operations.exec();
                }
            });

            if (result != null) {
                appendLog("buy", walletId, stockName);
                return BuyResult.OK;
            }
            // WATCH fired – retry
        }
        return BuyResult.INSUFFICIENT_BANK_STOCK; // edge-case safety
    }

    /**
     * Sell: wallet → bank.
     * Returns false if wallet has 0 of this stock.
     * Returns null if stock doesn't exist in bank at all.
     */
    public SellResult sell(String walletId, String stockName) {
        if (!stockExistsInBank(stockName)) {
            return SellResult.STOCK_NOT_FOUND;
        }

        for (int attempt = 0; attempt < 10; attempt++) {
            String wKey = walletKey(walletId);
            redisTemplate.watch(wKey);
            Object raw = redisTemplate.opsForHash().get(wKey, stockName);
            long walletQty = raw == null ? 0L : Long.parseLong(raw.toString());

            if (walletQty <= 0) {
                redisTemplate.unwatch();
                return SellResult.INSUFFICIENT_WALLET_STOCK;
            }

            @SuppressWarnings("unchecked")
            List<Object> result = (List<Object>) redisTemplate.execute(new SessionCallback<>() {
                @Override
                public Object execute(RedisOperations operations) throws DataAccessException {
                    operations.multi();
                    operations.opsForHash().increment(wKey, stockName, -1);
                    operations.opsForHash().increment(BANK_KEY, stockName, 1);
                    return operations.exec();
                }
            });

            if (result != null) {
                appendLog("sell", walletId, stockName);
                return SellResult.OK;
            }
        }
        return SellResult.INSUFFICIENT_WALLET_STOCK;
    }

    // ── Audit log ─────────────────────────────────────────────────────────────

    public List<LogEntry> getLog() {
        Long size = redisTemplate.opsForList().size(LOG_KEY);
        if (size == null || size == 0) return List.of();
        List<Object> raw = redisTemplate.opsForList().range(LOG_KEY, 0, size - 1);
        List<LogEntry> result = new ArrayList<>();
        if (raw != null) {
            for (Object o : raw) {
                String s = o.toString();
                // format: "type|walletId|stockName"
                String[] parts = s.split("\\|", 3);
                if (parts.length == 3) {
                    result.add(new LogEntry(parts[0], parts[1], parts[2]));
                }
            }
        }
        return result;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void appendLog(String type, String walletId, String stockName) {
        redisTemplate.opsForList().rightPush(LOG_KEY, type + "|" + walletId + "|" + stockName);
    }

    private String walletKey(String walletId) {
        return WALLET_KEY_PREFIX + walletId;
    }

    private List<StockEntry> toStockList(Map<Object, Object> map) {
        List<StockEntry> list = new ArrayList<>();
        for (Map.Entry<Object, Object> e : map.entrySet()) {
            list.add(new StockEntry(e.getKey().toString(), Long.parseLong(e.getValue().toString())));
        }
        return list;
    }

    // ── Result enums ──────────────────────────────────────────────────────────

    public enum BuyResult { OK, STOCK_NOT_FOUND, INSUFFICIENT_BANK_STOCK }
    public enum SellResult { OK, STOCK_NOT_FOUND, INSUFFICIENT_WALLET_STOCK }
}
