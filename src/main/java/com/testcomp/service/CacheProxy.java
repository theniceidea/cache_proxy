package com.testcomp.service;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.HMac;
import cn.hutool.crypto.digest.HmacAlgorithm;
import com.alibaba.fastjson2.TypeReference;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.*;
import lombok.SneakyThrows;
import net.lightbody.bmp.BrowserMobProxy;
import net.lightbody.bmp.BrowserMobProxyServer;
import net.lightbody.bmp.util.HttpMessageContents;
import net.lightbody.bmp.util.HttpMessageInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.cache.Cache;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.testcomp.config.bizdemo.CaffeineCacheConfig.BEAN_NAME2;

@Service
public class CacheProxy implements ApplicationRunner {
    private static final Logger logger = LoggerFactory.getLogger(CacheProxy.class);

    @Value("${app.proxyPort}")
    private int proxyPort;

    @Autowired
    @Qualifier(BEAN_NAME2)
    private CaffeineCacheManager caffeineCacheManager;
    private Cache cache = null;
    private HashMap<String, ConfItem> redirectMap = new HashMap<>();


    private HMac hmac = new HMac(HmacAlgorithm.HmacMD5);

    public void startProxy(){
        BrowserMobProxy proxy = new BrowserMobProxyServer();
        proxy.start(proxyPort);//设置端口
        proxy.addRequestFilter((request, contents, messageInfo) -> {
            String key = hashRequestKey(request, contents, messageInfo);
            if(StrUtil.isBlank(key)){
                change2redirectUri(request);
                return null;
            }

            CacheItem cacheItem = cache.get(key, CacheItem.class);
            long currentTimeMillis = System.currentTimeMillis();
            if(null == cacheItem || cacheItem.getExpireTime()<currentTimeMillis){
                cache.evictIfPresent(key);
                change2redirectUri(request);
                messageInfo.getOriginalRequest().headers().add("cache_key", key);
                return null;
            }

            final HttpResponseStatus status = HttpResponseStatus.OK;
            byte[] value1 = cacheItem.getBytes();
            ByteBuf buffer = Unpooled.buffer(value1.length);
            buffer.writeBytes(value1);
            final DefaultFullHttpResponse msg = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, buffer);

            List<Map.Entry<String, String>> entries = cacheItem.getHeaders().entries();
            HttpHeaders headers = msg.headers();
            for(Map.Entry<String, String> header : entries){
                if(Objects.equals("Via", header.getKey())){
                    continue;
                }
                headers.set(header.getKey(), header.getValue());
            }
            headers.add("From-Cache", "true");

            return msg;
        });

        proxy.addResponseFilter((response, contents, messageInfo) -> {
            if(HttpResponseStatus.OK.compareTo(response.status()) != 0){
                return;
            }
            HttpRequest originalRequest = messageInfo.getOriginalRequest();
            String cacheKey = originalRequest.headers().get("cache_key");
            if(StrUtil.isBlank(cacheKey)){
                return;
            }
            ConfItem confItem = getConfItem(originalRequest);
            if(null == confItem){
                return;
            }
            CacheItem cacheItem = new CacheItem();
            cacheItem.setHeaders(response.headers());
            cacheItem.setBytes(contents.getBinaryContents());
            cacheItem.setExpireTime(System.currentTimeMillis()+confItem.getExpireTimeMillis());
            cache.put(cacheKey, cacheItem);
        });
    }

    private boolean change2redirectUri(HttpRequest request){
        ConfItem confItem = getConfItem(request);
        if(null == confItem){
            return false;
        }

        String uri = request.uri();
        String queryString = StrUtil.subAfter(uri, "?", false);

        String nuri = confItem.getRedirect()+(StrUtil.isBlank(queryString)?"":"?")+queryString;
        request.setUri(nuri);
        return true;
    }
    private ConfItem getConfItem(HttpRequest request){
        String uri = request.uri();
        String uriKey = request.method().name()+":"+StrUtil.subBefore(uri, '?', false);
        if(StrUtil.isBlank(uriKey)){
            return null;
        }
        return redirectMap.get(uriKey);
    }

    private String hashRequestKey(HttpRequest request, HttpMessageContents contents, HttpMessageInfo messageInfo) {
        String uri = request.uri();
        String name = request.method().name();
        String authorization = request.headers().get("Authorization");
        String textContents = contents.getTextContents();
        StringBuffer buffer = new StringBuffer();
        buffer.append(uri);
        buffer.append(name);
        buffer.append(authorization);
        buffer.append(textContents);
        String text = buffer.toString();
        return md5(text);
    }

    private String md5(String text){
        try {
            return hmac.digestHex(text);
        }catch (Exception e){
            logger.error(e.getMessage(), e);
            return "";
        }
    }
    private void loadConfig(){
        File file = new File("conf.json");
        String text = FileUtil.readUtf8String(file);
        if(StrUtil.isBlank(text)){
            return;
        }
        redirectMap = new TypeReference<HashMap<String, ConfItem>>() {
        }.parseObject(text);
    }
    private void threadLoadConfig(){
        new Thread(()->{
            while (true) {
                try {
                    loadConfig();
                    logger.info("reload config");
                } catch (Exception e) {
                    logger.error(e.getMessage(), e);
                }finally {
                    sleep(60000);
                }
            }
        }).start();
    }
    @SneakyThrows
    private void sleep(long millis){
        Thread.sleep(millis);
    }
    @Override
    public void run(ApplicationArguments args) throws Exception {
        cache = caffeineCacheManager.getCache("proxy_cache");
        threadLoadConfig();
        startProxy();
    }
}
