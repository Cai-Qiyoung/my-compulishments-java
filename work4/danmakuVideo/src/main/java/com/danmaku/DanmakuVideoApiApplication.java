package com.danmaku;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.transaction.annotation.EnableTransactionManagement;

// 复合注解：包含@Configuration、@EnableAutoConfiguration、@ComponentScan
@SpringBootApplication
// 扫描Mapper接口，对应com.danmaku.mapper包
@MapperScan("com.danmaku.mapper")
// 开启事务管理
@EnableTransactionManagement
// 开启异步任务
@EnableAsync
public class DanmakuVideoApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(DanmakuVideoApiApplication.class, args);
    }
}