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

@Service
public class StockMarketService {

    private static final String BANK_KEY         = "bank:stocks";
    private static final String WALLET_KEY_PREFIX = "wallet:";
    private static final String LOG_KEY           = "audit:log";
    private static final int    MAX_RETRIES       = 20;

    private final RedisTemplate<String, Object> redisTemplate;

    public StockMarketService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // ── Bank

    public List<StockEntry> getBankStocks() {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(BANK_KEY);
        return toStockList(entries);
    }

    public void setBankStocks(List<StockEntry> stocks) {
        redisTemplate.execute(new SessionCallback<Object>() {
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

    public boolean stockExistsInBank(String stockName) {
        return redisTemplate.opsForHash().hasKey(BANK_KEY, stockName);
    }

    // ── Wallet

    public List<StockEntry> getWalletStocks(String walletId) {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(walletKey(walletId));
        return toStockList(entries);
    }

    public long getWalletStockQuantity(String walletId, String stockName) {
        Object val = redisTemplate.opsForHash().get(walletKey(walletId), stockName);
        return val == null ? 0L : Long.parseLong(val.toString());
    }

    // ── Trade operations

    /**
     * Atomically transfers one unit from bank to wallet.
     * Uses optimistic locking (WATCH/MULTI/EXEC) — safe across multiple instances.
     */
    public BuyResult buy(String walletId, String stockName) {
        if (!stockExistsInBank(stockName)) {
            return BuyResult.STOCK_NOT_FOUND;
        }

        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            BuyResult result = redisTemplate.execute(new SessionCallback<BuyResult>() {
                @Override
                @SuppressWarnings("unchecked")
                public BuyResult execute(RedisOperations operations) throws DataAccessException {
                    operations.watch(BANK_KEY);

                    Object raw = operations.opsForHash().get(BANK_KEY, stockName);
                    long bankQty = raw == null ? 0L : Long.parseLong(raw.toString());

                    if (bankQty <= 0) {
                        operations.unwatch();
                        return BuyResult.INSUFFICIENT_BANK_STOCK;
                    }

                    operations.multi();
                    operations.opsForHash().increment(BANK_KEY, stockName, -1);
                    operations.opsForHash().increment(walletKey(walletId), stockName, 1);
                    List<Object> exec = operations.exec();

                    return exec != null ? BuyResult.OK : null; // null = WATCH fired, retry
                }
            });

            if (result == BuyResult.OK) {
                appendLog("buy", walletId, stockName);
                return BuyResult.OK;
            }
            if (result == BuyResult.INSUFFICIENT_BANK_STOCK) {
                return BuyResult.INSUFFICIENT_BANK_STOCK;
            }
            // result == null: WATCH fired due to concurrent modification, retry
        }
        return BuyResult.INSUFFICIENT_BANK_STOCK;
    }

    /**
     * Atomically transfers one unit from wallet to bank.
     * Uses optimistic locking (WATCH/MULTI/EXEC) — safe across multiple instances.
     */
    public SellResult sell(String walletId, String stockName) {
        if (!stockExistsInBank(stockName)) {
            return SellResult.STOCK_NOT_FOUND;
        }

        String wKey = walletKey(walletId);

        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            SellResult result = redisTemplate.execute(new SessionCallback<SellResult>() {
                @Override
                @SuppressWarnings("unchecked")
                public SellResult execute(RedisOperations operations) throws DataAccessException {
                    operations.watch(wKey);

                    Object raw = operations.opsForHash().get(wKey, stockName);
                    long walletQty = raw == null ? 0L : Long.parseLong(raw.toString());

                    if (walletQty <= 0) {
                        operations.unwatch();
                        return SellResult.INSUFFICIENT_WALLET_STOCK;
                    }

                    operations.multi();
                    operations.opsForHash().increment(wKey, stockName, -1);
                    operations.opsForHash().increment(BANK_KEY, stockName, 1);
                    List<Object> exec = operations.exec();

                    return exec != null ? SellResult.OK : null;
                }
            });

            if (result == SellResult.OK) {
                appendLog("sell", walletId, stockName);
                return SellResult.OK;
            }
            if (result == SellResult.INSUFFICIENT_WALLET_STOCK) {
                return SellResult.INSUFFICIENT_WALLET_STOCK;
            }
        }
        return SellResult.INSUFFICIENT_WALLET_STOCK;
    }

    // ── Audit log

    public List<LogEntry> getLog() {
        List<Object> raw = redisTemplate.opsForList().range(LOG_KEY, 0, -1);
        List<LogEntry> entries = new ArrayList<>();
        if (raw != null) {
            for (Object o : raw) {
                String[] parts = o.toString().split("\\|", 3);
                if (parts.length == 3) {
                    entries.add(new LogEntry(parts[0], parts[1], parts[2]));
                }
            }
        }
        return entries;
    }

    // ── Helpers

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

    // ── Result enums

    public enum BuyResult  { OK, STOCK_NOT_FOUND, INSUFFICIENT_BANK_STOCK }
    public enum SellResult { OK, STOCK_NOT_FOUND, INSUFFICIENT_WALLET_STOCK }
}
