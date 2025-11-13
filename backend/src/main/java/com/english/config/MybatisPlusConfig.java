//为MyBatis-Plus 框架配置分页插件
//以支持数据库的分页查询功能
//依赖 MyBatis-Plus 框架，通常用于 Spring Boot 项目中整合 MyBatis-Plus 进行数据库操作。
//适用于需要分页查询数据库数据的场景（如列表页分页展示数据），避免手动编写复杂的分页 SQL，提高开发效率。

package com.english.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
//标识这是一个 Spring 配置类，Spring 会自动扫描并加载该类中的 Bean 定义

public class MybatisPlusConfig {// 自定义的MyBatis-Plus配置类

    @Bean// 将方法返回的对象注册为Spring容器中的Bean，供其他组件自动注入使用
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 分页插件
        // 向拦截器容器中添加分页插件：
        // 1. 创建分页插件对象，指定数据库类型为MySQL（适配MySQL的分页语法，如LIMIT）
        // 2. 通过addInnerInterceptor方法将分页插件添加到容器中
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}