package com.cityhub.utils;

public interface ILock {
    boolean tryLock(long timeoutSec);
    void unlock();
}
