package com.cityhub;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@MapperScan("com.cityhub.mapper")
@SpringBootApplication
@EnableAspectJAutoProxy(exposeProxy = true)
public class CityHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(CityHubApplication.class, args);
    }

}