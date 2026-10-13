package com.cityhub.utils;

import lombok.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class simpleRedisLock implements ILock {
    public simpleRedisLock(StringRedisTemplate stringRedisTemplate, String name) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.name = name;
    }private  static  final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    private StringRedisTemplate stringRedisTemplate;
    private String name;

    private static final String KEY_PREFIX = "lock:";
private static final String ID_PREFIX=UUID.randomUUID().toString();




    @Override
    public boolean tryLock(long timeoutSec) {

       String threadId = Thread.currentThread().getId()+ID_PREFIX;
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX+name, threadId, timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);

    }

    @Override
    public void unlock() {
//        线程标识


        stringRedisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(KEY_PREFIX+name),ID_PREFIX+Thread.currentThread().getId());
//        String threadId = ID_PREFIX+Thread.currentThread().getId();
//        // 锁标识
//        String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX+name);
//        if (threadId.equals(id)){
//            stringRedisTemplate.delete(KEY_PREFIX+name);
        }

    }

