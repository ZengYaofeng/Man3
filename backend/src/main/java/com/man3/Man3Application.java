package com.man3;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 漫画爬虫启动类
 */
@SpringBootApplication
@EnableScheduling
@MapperScan("com.man3.mapper")
public class Man3Application {

    public static void main(String[] args) {
        SpringApplication.run(Man3Application.class, args);
    }
}
