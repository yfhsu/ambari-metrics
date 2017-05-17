/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.metrics2.host.aggregator;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.apache.hadoop.metrics2.sink.timeline.TimelineMetrics;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Singleton class with 2 guava caches for raw and aggregated metrics storing
 */
public class TimelineMetricsHolder {
    private static final int DEFAULT_RAW_CACHE_EXPIRE_TIME = 60;
    private static final int DEFAULT_AGGREGATION_CACHE_EXPIRE_TIME = 300;
    private Cache<Long, TimelineMetrics> aggregationMetricsCache;
    private Cache<Long, TimelineMetrics> rawMetricsCache;
    private static TimelineMetricsHolder instance = null;
    //to ensure no metric values are expired
    private static int EXPIRE_DELAY = 30;
    ReadWriteLock aggregationCacheLock = new ReentrantReadWriteLock();
    ReadWriteLock rawCacheLock = new ReentrantReadWriteLock();

    private TimelineMetricsHolder(int rawCacheExpireTime, int aggregationCacheExpireTime) {
        this.rawMetricsCache = CacheBuilder.newBuilder().expireAfterWrite(rawCacheExpireTime + EXPIRE_DELAY, TimeUnit.SECONDS).build();
        this.aggregationMetricsCache = CacheBuilder.newBuilder().expireAfterWrite(aggregationCacheExpireTime + EXPIRE_DELAY, TimeUnit.SECONDS).build();
    }

    public static TimelineMetricsHolder getInstance(int rawCacheExpireTime, int aggregationCacheExpireTime) {
        if (instance == null) {
            instance = new TimelineMetricsHolder(rawCacheExpireTime, aggregationCacheExpireTime);
        }
        return instance;
    }

    /**
     * Uses default expiration time for caches initialization if they are not initialized yet.
     * @return
     */
    public static TimelineMetricsHolder getInstance() {
        return getInstance(DEFAULT_RAW_CACHE_EXPIRE_TIME, DEFAULT_AGGREGATION_CACHE_EXPIRE_TIME);
    }

    public void putMetricsForAggregationPublishing(TimelineMetrics timelineMetrics) {
        aggregationCacheLock.writeLock().lock();
        aggregationMetricsCache.put(System.currentTimeMillis(), timelineMetrics);
        aggregationCacheLock.writeLock().unlock();
    }

    public Map<Long, TimelineMetrics> extractMetricsForAggregationPublishing() {
        return extractMetricsFromCacheWithLock(aggregationMetricsCache, aggregationCacheLock);
    }

    public void putMetricsForRawPublishing(TimelineMetrics metrics) {
        rawCacheLock.writeLock().lock();
        rawMetricsCache.put(System.currentTimeMillis(), metrics);
        rawCacheLock.writeLock().unlock();
    }

    public Map<Long, TimelineMetrics> extractMetricsForRawPublishing() {
        return extractMetricsFromCacheWithLock(rawMetricsCache, rawCacheLock);
    }

    /**
     * Returns values from cache and clears the cache
     * @param cache
     * @param lock
     * @return
     */
    private Map<Long, TimelineMetrics> extractMetricsFromCacheWithLock(Cache<Long, TimelineMetrics> cache, ReadWriteLock lock) {
        lock.writeLock().lock();
        Map<Long, TimelineMetrics> metricsMap = new TreeMap<>(cache.asMap());
        cache.invalidateAll();
        lock.writeLock().unlock();
        return metricsMap;
    }

}
