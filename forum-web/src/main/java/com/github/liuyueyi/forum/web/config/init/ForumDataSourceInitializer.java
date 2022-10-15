package com.github.liuyueyi.forum.web.config.init;

import com.github.liuyueyi.forum.core.util.SpringUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.DataSourceInitializer;
import org.springframework.jdbc.datasource.init.DatabasePopulator;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.util.CollectionUtils;

import javax.sql.DataSource;
import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * 表初始化，只有首次启动时，才会执行
 *
 * @author YiHui
 * @date 2022/10/15
 */
@Slf4j
@Configuration
public class ForumDataSourceInitializer {
    @Value("classpath:schema-all.sql")
    private Resource schemaSql;
    @Value("classpath:init-data.sql")
    private Resource initData;
    @Value("${database.name}")
    private String database;

    @Bean
    public DataSourceInitializer dataSourceInitializer(final DataSource dataSource) {
        final DataSourceInitializer initializer = new DataSourceInitializer();
        // 设置数据源
        initializer.setDataSource(dataSource);
        initializer.setDatabasePopulator(databasePopulator());
        initializer.setEnabled(needInit(dataSource));
        return initializer;
    }

    private DatabasePopulator databasePopulator() {
        final ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.addScripts(schemaSql);
        populator.addScripts(initData);
        populator.setSeparator(";");
        return populator;
    }

    /**
     * 检测一下数据库中表是否存在，若存在则不初始化；否则基于 schema-all.sql 进行初始化表
     *
     * @param dataSource
     * @return
     */
    private boolean needInit(DataSource dataSource) {
        try {
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            List list = jdbcTemplate.queryForList("SELECT table_name FROM information_schema.TABLES where table_name = 'user_info' and table_schema = '" + database + "';");
            return CollectionUtils.isEmpty(list);
        } catch (Exception e) {
            // 查询失败，可能是数据库不存在，尝试创建数据库之后再次测试
            URI url = URI.create(SpringUtil.getConfig("spring.datasource.url").substring(5));
            String uname = SpringUtil.getConfig("spring.datasource.username");
            String pwd = SpringUtil.getConfig("spring.datasource.password");
            try (Connection connection = DriverManager.getConnection("jdbc:mysql://" + url.getHost() + ":" + url.getPort() +
                    "?useUnicode=true&characterEncoding=UTF-8&useSSL=false", uname, pwd);
                 Statement statement = connection.createStatement()) {
                String createDb = "CREATE DATABASE IF NOT EXISTS " + database;
                connection.setAutoCommit(false);
                statement.execute(createDb);
                connection.commit();
                log.info("创建数据库（{}）成功", database);
                return true;
            } catch (SQLException e2) {
                throw new RuntimeException(e2);
            }
        }
    }
}
