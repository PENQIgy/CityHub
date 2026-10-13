package com.cityhub.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.cityhub.entity.Shop;
import io.lettuce.core.GeoArgs;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.cityhub.service.impl.ShopServiceImpl.CACHE_REBUILD_EXECUTOR;
import static com.cityhub.utils.RedisConstants.*;

@Slf4j
@Component
public class cacheClient {
    private final RedisTemplate redisTemplate;
    private final StringRedisTemplate stringRedisTemplate;

    public cacheClient(RedisTemplate redisTemplate, StringRedisTemplate stringRedisTemplate) {
        this.redisTemplate = redisTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
    }
//    存入redis
  public  void  set(String key, Object value, long time, TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time,unit);
    }
//    存入redis并设置逻辑过期时间
public  void setWithLogicalExpire(String key, Object value, long time, TimeUnit unit){
    RedisData redisData = new RedisData();
    redisData.setData(value);
    redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
    stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(redisData));
}
    public <T,ID>T queryWithPassThrough
            (String prefix, ID id, Class<T>type,Function<ID,T> dbFallback,long time, TimeUnit unit) {
        String key = prefix + id;
        // 1.从redis查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2.判断是否存在
        if (StrUtil.isNotBlank(json)) {
            // 3.存在，直接返回
            return JSONUtil.toBean(json, type);
        }
        // 判断命中的是否是空值（json != null 代表取到了""空字符串）
        if (json != null) {
            // 返回一个错误信息
            return null;
        }
        // 4.不存在，根据id查询数据库
       T t = dbFallback.apply(id);
        // 5.不存在，返回错误
        if (t == null) {
            // 将空值写入redis，设置过期时间，防止缓存穿透
            stringRedisTemplate.opsForValue().set(key, "", CACHE_SHOP_TTL, TimeUnit.MINUTES);
            return null;
        }
        // 6.存在，写入redis
        this.set(key,t,time,unit);
        // 7.返回
        return t;
    }






    public <T,ID> T queryWithLogicalExpire(String prefix,ID id,Class<T> type,Function<ID,T> dbFallback,long time, TimeUnit unit){
        String key=prefix+id;
        String Json = stringRedisTemplate.opsForValue().get(key);
        if(StrUtil.isBlank(Json)){
            return  null;
        }
        RedisData redisData= JSONUtil.toBean(Json, RedisData.class);
        JSONObject jsonObject = (JSONObject) redisData.getData();
        T t = JSONUtil.toBean(jsonObject,type);
        LocalDateTime expireTime = redisData.getExpireTime();

        if (expireTime.isAfter(LocalDateTime.now())){
            return t;
        }
        String locKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(locKey);
        if (isLock) {
            try {
                CACHE_REBUILD_EXECUTOR.submit(() -> {
                    try {
                        T t2 = dbFallback.apply(id);
                        this.setWithLogicalExpire(key, t2, time, unit);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    } finally {
                        deletelock(locKey);
                    }
                });
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        return t;

    }

    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private  void deletelock(String key){
        stringRedisTemplate.delete(key);
    }




}