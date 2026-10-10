package com.mkt.infra.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import com.mkt.infra.redis.KeyValueStore;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.json.JsonUtil;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Caffeine L1 + Redis L2 + {@code cache:evict} broadcast (design §6.2). */
public final class TwoLevelPlatformCache implements PlatformCache {

    public static final String EVICT_CHANNEL = "cache:evict";

    private static final Logger log = LoggerFactory.getLogger(TwoLevelPlatformCache.class);

    private final KeyValueStore store;
    private final Map<CacheNamespace, Cache<String, Object>> l1 = new EnumMap<>(CacheNamespace.class);
    private final Map<CacheNamespace, AtomicLong> hits = new EnumMap<>(CacheNamespace.class);
    private final Map<CacheNamespace, AtomicLong> misses = new EnumMap<>(CacheNamespace.class);
    /** Per-key generation bumped on KEY/PREFIX evict (local + broadcast). */
    private final ConcurrentHashMap<String, AtomicLong> keyGenerations = new ConcurrentHashMap<>();
    /** Namespace generation bumped on NAMESPACE evict (local + broadcast). */
    private final Map<CacheNamespace, AtomicLong> namespaceGenerations = new EnumMap<>(CacheNamespace.class);
    /** In-flight same-key loaders (single-flight). */
    private final ConcurrentHashMap<String, CompletableFuture<?>> inflight = new ConcurrentHashMap<>();

    public TwoLevelPlatformCache(KeyValueStore store) {
        this(store, Ticker.systemTicker());
    }

    TwoLevelPlatformCache(KeyValueStore store, Ticker ticker) {
        this.store = store;
        for (CacheNamespace ns : CacheNamespace.values()) {
            hits.put(ns, new AtomicLong());
            misses.put(ns, new AtomicLong());
            namespaceGenerations.put(ns, new AtomicLong());
            if (ns.kind() == CacheNamespaceKind.MANAGED) {
                l1.put(
                        ns,
                        Caffeine.newBuilder()
                                .ticker(ticker)
                                .maximumSize(ns.l1Capacity())
                                .expireAfterWrite(ns.ttl())
                                .build());
            }
        }
        store.subscribe(EVICT_CHANNEL, this::onBroadcast);
    }

    @Override
    public <T> T get(CacheNamespace namespace, String bizKey, Class<T> type, Supplier<T> loader) {
        rejectSession(namespace);
        if (namespace.kind() == CacheNamespaceKind.PLACEHOLDER) {
            return loader.get();
        }
        Cache<String, Object> local = l1.get(namespace);
        Object cached = local.getIfPresent(bizKey);
        if (cached != null) {
            hits.get(namespace).incrementAndGet();
            return type.cast(cached);
        }
        try {
            String raw = store.get(namespace.redisKey(bizKey));
            if (raw != null) {
                T value = deserialize(raw, type);
                local.put(bizKey, value);
                hits.get(namespace).incrementAndGet();
                return value;
            }
        } catch (RuntimeException ex) {
            log.warn("L2 unreachable, falling back to loader, ns={}", namespace.id(), ex);
        }
        misses.get(namespace).incrementAndGet();
        return loadMerged(namespace, bizKey, loader);
    }

    @Override
    public void put(CacheNamespace namespace, String bizKey, Object value) {
        rejectSession(namespace);
        if (namespace.kind() == CacheNamespaceKind.PLACEHOLDER || value == null) {
            return;
        }
        putQuiet(namespace, bizKey, value);
    }

    @Override
    public void putAfterCommit(CacheNamespace namespace, String bizKey, Object value) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            put(namespace, bizKey, value);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                put(namespace, bizKey, value);
            }
        });
    }

    @Override
    public void evict(CacheNamespace namespace, String bizKey) {
        rejectSession(namespace);
        if (namespace.kind() == CacheNamespaceKind.PLACEHOLDER) {
            return;
        }
        bumpKeyGeneration(namespace, bizKey);
        unlinkQuiet(namespace.redisKey(bizKey));
        Cache<String, Object> local = l1.get(namespace);
        if (local != null) {
            local.invalidate(bizKey);
        }
        publish(new CacheEvictMessage(namespace.id(), bizKey, CacheEvictMessage.EvictMode.KEY));
    }

    @Override
    public void evictPrefix(CacheNamespace namespace, String prefix) {
        rejectSession(namespace);
        if (namespace.kind() == CacheNamespaceKind.PLACEHOLDER) {
            return;
        }
        bumpPrefixGeneration(namespace, prefix);
        unlinkPatternQuiet(namespace.redisKey(prefix) + "*");
        Cache<String, Object> local = l1.get(namespace);
        if (local != null) {
            local.asMap().keySet().removeIf(k -> k.startsWith(prefix));
        }
        publish(new CacheEvictMessage(namespace.id(), prefix, CacheEvictMessage.EvictMode.PREFIX));
    }

    @Override
    public void evictNamespace(CacheNamespace namespace) {
        rejectSession(namespace);
        if (namespace.kind() == CacheNamespaceKind.PLACEHOLDER) {
            return;
        }
        namespaceGenerations.get(namespace).incrementAndGet();
        unlinkPatternQuiet(namespace.id() + ":*");
        Cache<String, Object> local = l1.get(namespace);
        if (local != null) {
            local.invalidateAll();
        }
        publish(new CacheEvictMessage(namespace.id(), null, CacheEvictMessage.EvictMode.NAMESPACE));
    }

    @Override
    public void evictAfterCommit(CacheNamespace namespace, String bizKey) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            evict(namespace, bizKey);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                evict(namespace, bizKey);
            }
        });
    }

    @Override
    public CacheStatsView stats(CacheNamespace namespace) {
        if (namespace.kind() == CacheNamespaceKind.SA_TOKEN) {
            return CacheStatsView.na(namespace.id());
        }
        if (namespace.kind() == CacheNamespaceKind.PLACEHOLDER) {
            return CacheStatsView.zeros(namespace.id());
        }
        long hit = hits.get(namespace).get();
        long miss = misses.get(namespace).get();
        long total = hit + miss;
        String rate = total == 0 ? "N/A" : String.valueOf(hit * 100 / total);
        long keys = l1.get(namespace).estimatedSize();
        return new CacheStatsView(namespace.id(), String.valueOf(keys), rate);
    }

    void onBroadcast(String payload) {
        CacheEvictMessage message = JsonUtil.fromJson(payload, CacheEvictMessage.class);
        CacheNamespace.ofId(message.namespace()).ifPresent(ns -> {
            Cache<String, Object> local = l1.get(ns);
            if (local == null) {
                return;
            }
            switch (message.mode()) {
                case KEY -> {
                    bumpKeyGeneration(ns, message.key());
                    local.invalidate(message.key());
                }
                case PREFIX -> {
                    bumpPrefixGeneration(ns, message.key());
                    local.asMap().keySet().removeIf(k -> k.startsWith(message.key()));
                }
                case NAMESPACE -> {
                    namespaceGenerations.get(ns).incrementAndGet();
                    local.invalidateAll();
                }
            }
        });
    }

    private <T> T loadMerged(CacheNamespace namespace, String bizKey, Supplier<T> loader) {
        String flightKey = flightKey(namespace, bizKey);
        long keyGen = keyGeneration(namespace, bizKey);
        long nsGen = namespaceGenerations.get(namespace).get();

        CompletableFuture<T> created = new CompletableFuture<>();
        @SuppressWarnings("unchecked")
        CompletableFuture<T> existing = (CompletableFuture<T>) inflight.putIfAbsent(flightKey, created);
        if (existing != null) {
            return joinFlight(existing);
        }
        try {
            T loaded = loader.get();
            if (loaded != null && stillValid(namespace, bizKey, keyGen, nsGen)) {
                putQuiet(namespace, bizKey, loaded);
            }
            created.complete(loaded);
            return loaded;
        } catch (RuntimeException ex) {
            created.completeExceptionally(ex);
            throw ex;
        } catch (Exception ex) {
            created.completeExceptionally(ex);
            throw new CompletionException(ex);
        } finally {
            inflight.remove(flightKey, created);
        }
    }

    private static <T> T joinFlight(CompletableFuture<T> future) {
        try {
            return future.join();
        } catch (CompletionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw ex;
        }
    }

    private boolean stillValid(CacheNamespace namespace, String bizKey, long keyGen, long nsGen) {
        return keyGeneration(namespace, bizKey) == keyGen
                && namespaceGenerations.get(namespace).get() == nsGen;
    }

    private long keyGeneration(CacheNamespace namespace, String bizKey) {
        return keyGenerations.computeIfAbsent(flightKey(namespace, bizKey), k -> new AtomicLong()).get();
    }

    private void bumpKeyGeneration(CacheNamespace namespace, String bizKey) {
        keyGenerations.computeIfAbsent(flightKey(namespace, bizKey), k -> new AtomicLong()).incrementAndGet();
    }

    private void bumpPrefixGeneration(CacheNamespace namespace, String prefix) {
        String nsPrefix = namespace.id() + '\0';
        for (Map.Entry<String, AtomicLong> entry : keyGenerations.entrySet()) {
            String flight = entry.getKey();
            if (!flight.startsWith(nsPrefix)) {
                continue;
            }
            String key = flight.substring(nsPrefix.length());
            if (key.startsWith(prefix)) {
                entry.getValue().incrementAndGet();
            }
        }
    }

    private static String flightKey(CacheNamespace namespace, String bizKey) {
        return namespace.id() + '\0' + bizKey;
    }

    private void putQuiet(CacheNamespace namespace, String bizKey, Object value) {
        l1.get(namespace).put(bizKey, value);
        try {
            store.set(namespace.redisKey(bizKey), JsonUtil.toJson(value), namespace.ttl());
        } catch (RuntimeException ex) {
            log.warn("L2 put failed, L1 kept, ns={}", namespace.id(), ex);
        }
    }

    private void unlinkQuiet(String key) {
        try {
            store.unlink(key);
        } catch (RuntimeException ex) {
            log.warn("L2 unlink failed, key={}", key, ex);
        }
    }

    private void unlinkPatternQuiet(String pattern) {
        try {
            store.unlinkByPattern(pattern);
        } catch (RuntimeException ex) {
            log.warn("L2 unlink pattern failed, pattern={}", pattern, ex);
        }
    }

    private void publish(CacheEvictMessage message) {
        try {
            store.publish(EVICT_CHANNEL, JsonUtil.toJson(message));
        } catch (RuntimeException ex) {
            log.warn("cache:evict publish failed, ns={}", message.namespace(), ex);
        }
    }

    private static void rejectSession(CacheNamespace namespace) {
        if (namespace.kind() == CacheNamespaceKind.SA_TOKEN) {
            throw new BusinessException(CacheErrorCodes.SESSION_FORBIDDEN);
        }
    }

    private static <T> T deserialize(String raw, Class<T> type) {
        if (type == String.class) {
            if (raw.length() >= 2 && raw.charAt(0) == '"') {
                return type.cast(JsonUtil.fromJson(raw, String.class));
            }
            return type.cast(raw);
        }
        return JsonUtil.fromJson(raw, type);
    }
}
