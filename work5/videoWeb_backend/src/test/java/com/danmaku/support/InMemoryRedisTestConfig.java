package com.danmaku.support;

import com.danmaku.model.ChatMessagePayload;
import com.danmaku.mq.ChatMessageProducer;
import com.danmaku.util.RedisUtil;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.ZSetOperations;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@TestConfiguration
public class InMemoryRedisTestConfig {

    @Bean
    @Primary
    public RedisUtil redisUtil() {
        return new InMemoryRedisUtil();
    }

    @Bean
    @Primary
    public ChatMessageProducer chatMessageProducer() {
        return new ChatMessageProducer() {
            @Override
            public void send(ChatMessagePayload payload) {
            }
        };
    }

    static class InMemoryRedisUtil extends RedisUtil {
        private final Map<String, Object> cache = new ConcurrentHashMap<>();
        private final Map<String, List<Object>> lists = new ConcurrentHashMap<>();
        private final Map<String, Map<Object, Double>> zsets = new ConcurrentHashMap<>();
        private final ZSetOperations<String, Object> zSetOperations = buildZSetOperations();

        @Override
        public ZSetOperations<String, Object> opsForZSet() {
            return zSetOperations;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getCacheObject(String key) {
            return (T) cache.get(key);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getCacheObjectSafely(String key) {
            return (T) cache.get(key);
        }

        @Override
        public void setCacheObject(String key, Object value, long timeout, TimeUnit unit) {
            cache.put(key, value);
        }

        @Override
        public boolean setCacheObjectSafely(String key, Object value, long timeout, TimeUnit unit) {
            cache.put(key, value);
            return true;
        }

        @Override
        public Boolean deleteObject(String key) {
            boolean removed = cache.remove(key) != null;
            removed = lists.remove(key) != null || removed;
            removed = zsets.remove(key) != null || removed;
            return removed;
        }

        @Override
        public Long deleteObjects(Set<String> keys) {
            if (keys == null || keys.isEmpty()) {
                return 0L;
            }
            long count = 0L;
            for (String key : keys) {
                if (Boolean.TRUE.equals(deleteObject(key))) {
                    count++;
                }
            }
            return count;
        }

        @Override
        public Long deleteByPrefix(String prefix) {
            List<String> keys = new ArrayList<>();
            keys.addAll(cache.keySet().stream().filter(key -> key.startsWith(prefix)).toList());
            keys.addAll(lists.keySet().stream().filter(key -> key.startsWith(prefix)).toList());
            keys.addAll(zsets.keySet().stream().filter(key -> key.startsWith(prefix)).toList());
            return deleteObjects(new LinkedHashSet<>(keys));
        }

        @Override
        public <T> long rightPush(String key, T value) {
            List<Object> values = lists.computeIfAbsent(key, unused -> new ArrayList<>());
            values.add(value);
            return values.size();
        }

        @Override
        public void trim(String key, long start, long end) {
            List<Object> values = lists.get(key);
            if (values == null || values.isEmpty()) {
                return;
            }
            int from = (int) Math.max(0, start);
            int to = (int) Math.min(values.size() - 1, end);
            if (from > to) {
                values.clear();
                return;
            }
            List<Object> trimmed = new ArrayList<>(values.subList(from, to + 1));
            values.clear();
            values.addAll(trimmed);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> List<T> getListRange(String key, long start, long end) {
            List<Object> values = lists.getOrDefault(key, Collections.emptyList());
            if (values.isEmpty()) {
                return Collections.emptyList();
            }
            int from = start < 0 ? Math.max(0, values.size() + (int) start) : (int) start;
            int to = end < 0 ? values.size() + (int) end : (int) end;
            to = Math.min(to, values.size() - 1);
            if (from > to) {
                return Collections.emptyList();
            }
            return (List<T>) new ArrayList<>(values.subList(from, to + 1));
        }

        @Override
        public void expire(String key, long timeout, TimeUnit unit) {
        }

        @Override
        public boolean tryLock(String key, String value, long timeout, TimeUnit unit) {
            return cache.putIfAbsent(key, value) == null;
        }

        @Override
        public boolean unlock(String key, String value) {
            Object current = cache.get(key);
            if (value.equals(current)) {
                cache.remove(key);
                return true;
            }
            return false;
        }

        private ZSetOperations<String, Object> buildZSetOperations() {
            return (ZSetOperations<String, Object>) Proxy.newProxyInstance(
                    ZSetOperations.class.getClassLoader(),
                    new Class[]{ZSetOperations.class},
                    (proxy, method, args) -> {
                        String name = method.getName();
                        if ("add".equals(name)) {
                            String key = (String) args[0];
                            Object value = args[1];
                            double score = (Double) args[2];
                            zsets.computeIfAbsent(key, unused -> new ConcurrentHashMap<>()).put(value, score);
                            return true;
                        }
                        if ("incrementScore".equals(name)) {
                            String key = (String) args[0];
                            Object value = args[1];
                            double delta = (Double) args[2];
                            Map<Object, Double> values = zsets.computeIfAbsent(key, unused -> new ConcurrentHashMap<>());
                            double next = values.getOrDefault(value, 0D) + delta;
                            values.put(value, next);
                            return next;
                        }
                        if ("reverseRangeWithScores".equals(name)) {
                            String key = (String) args[0];
                            long start = (Long) args[1];
                            long end = (Long) args[2];
                            Map<Object, Double> values = zsets.getOrDefault(key, Collections.emptyMap());
                            List<Map.Entry<Object, Double>> sorted = values.entrySet().stream()
                                    .sorted(Map.Entry.<Object, Double>comparingByValue(Comparator.reverseOrder()))
                                    .toList();
                            if (sorted.isEmpty() || start >= sorted.size()) {
                                return Collections.emptySet();
                            }
                            int from = (int) start;
                            int to = (int) Math.min(end + 1, sorted.size());
                            Set<ZSetOperations.TypedTuple<Object>> result = new LinkedHashSet<>();
                            for (Map.Entry<Object, Double> entry : sorted.subList(from, to)) {
                                result.add(new DefaultTypedTuple<>(entry.getKey(), entry.getValue()));
                            }
                            return result;
                        }
                        if ("zCard".equals(name)) {
                            String key = (String) args[0];
                            return (long) zsets.getOrDefault(key, Collections.emptyMap()).size();
                        }
                        throw new UnsupportedOperationException("Unsupported ZSet operation in tests: " + name);
                    }
            );
        }
    }
}
