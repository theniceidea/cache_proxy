package com.testcomp.config.bizdemo;


import com.alibaba.fastjson.JSON;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;


@Configuration
public class CaffeineCacheConfig {
    public static final String BEAN_NAME = "caffeineCacheManager";
    public static final String BEAN_NAME2 = "caffeineCacheManager2";
    public static final String BEAN_KEYGENERATOR_NAME = "jsonKeyGenerator";
    public CaffeineCacheConfig(DefaultListableBeanFactory factory) {
        factory.getBeanDefinition(BEAN_NAME).setAutowireCandidate(false);
    }

    @Bean(BEAN_KEYGENERATOR_NAME)
    public KeyGenerator jsonKeyGenerator() {
        return (target, method, params) -> {
            if(null==params || params.length<=0){
                return "{}";
            }
            StringBuilder sb = new StringBuilder();
            for(Object obj : params){
                sb.append(JSON.toJSONString(obj));
            }
            return sb.toString();
        };
    }
    /**
     * initialCapacity=[integer]: 初始的缓存空间大小
     * maximumSize=[long]: 缓存的最大条数
     * maximumWeight=[long]: 缓存的最大权重
     * expireAfterAccess=[duration]: 最后一次写入或访问后经过固定时间过期
     * expireAfterWrite=[duration]: 最后一次写入后经过固定时间过期
     * refreshAfterWrite=[duration]: 创建缓存或者最近一次更新缓存后经过固定的时间间隔，刷新缓存
     * weakKeys: 打开key的弱引用
     * weakValues：打开value的弱引用
     * softValues：打开value的软引用
     * recordStats：开发统计功能 注意：
     * expireAfterWrite和expireAfterAccess同事存在时，以expireAfterWrite为准。
     * maximumSize和maximumWeight不可以同时使用
     * weakValues和softValues不可以同时使用
     * @return
     */
    @Bean(BEAN_NAME)
    public CaffeineCacheManager caffeineCacheManager(){
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        //Caffeine配置
        Caffeine<Object, Object> caffeine = Caffeine.newBuilder()
                //最后一次写入后经过固定时间过期
                .expireAfterWrite(10, TimeUnit.MINUTES)
                //maximumSize=[long]: 缓存的最大条数
                .maximumSize(200000);
        cacheManager.setCaffeine(caffeine);
        return cacheManager;
    }
    @Bean(BEAN_NAME2)
    public CaffeineCacheManager caffeineCacheManager2(){
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        //Caffeine配置
        Caffeine<Object, Object> caffeine = Caffeine.newBuilder()
                //最后一次写入后经过固定时间过期
                .expireAfterWrite(24, TimeUnit.HOURS)
                //maximumSize=[long]: 缓存的最大条数
                .maximumSize(200000);
        cacheManager.setCaffeine(caffeine);
        return cacheManager;
    }
}
