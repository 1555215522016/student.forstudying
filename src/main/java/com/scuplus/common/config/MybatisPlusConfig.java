package com.scuplus.common.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置
 *
 * 分页插件：让 Page 对象在 Mapper 方法里自动拼接 LIMIT 分页 SQL，
 * 不用手写 LIMIT #{pageSize} OFFSET #{offset}。
 *
 * 用法（后续模块会看到）：
 *   IPage<Grade> page = gradeMapper.selectPage(
 *       new Page<>(pageNum, pageSize), queryWrapper);
 *   // 返回的 page 里有 total 和 records，直接塞进 PageResult
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 分页插件，声明数据库类型 MySQL（分页方言不同）
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        // 单页最大条数：防止恶意大分页（如 LIMIT 1000000）拖垮数据库
        pagination.setMaxLimit(500L);
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }
}
