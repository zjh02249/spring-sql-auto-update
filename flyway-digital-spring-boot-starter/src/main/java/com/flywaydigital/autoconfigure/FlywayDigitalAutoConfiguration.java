package com.cbkj.infrastructure.autoconfigure;

import com.cbkj.infrastructure.core.FlywayDigital;
import com.cbkj.infrastructure.core.config.FlywayDigitalConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

import javax.sql.DataSource;
import java.util.Objects;

/**
 * FlywayDigital 自动配置类
 * 在Spring Boot启动时自动配置并执行数据库迁移
 */
@Configuration
@ConditionalOnClass(FlywayDigital.class)
@ConditionalOnProperty(prefix = "flyway-digital", name = "enabled", havingValue = "true", matchIfMissing = true)
@AutoConfigureAfter(DataSourceAutoConfiguration.class)
@EnableConfigurationProperties(FlywayDigitalProperties.class)
public class FlywayDigitalAutoConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(FlywayDigitalAutoConfiguration.class);

    private final FlywayDigitalProperties properties;
    private final ResourceLoader resourceLoader;

    @Autowired
    public FlywayDigitalAutoConfiguration(FlywayDigitalProperties properties, ResourceLoader resourceLoader) {
        this.properties = properties;
        this.resourceLoader = resourceLoader;
    }

    /**
     * 创建FlywayDigitalConfig Bean
     */
    @Bean
    @ConditionalOnMissingBean
    public FlywayDigitalConfig flywayDigitalConfig() {
        LOGGER.info("[FlywayDigitalAutoConfiguration] Creating FlywayDigitalConfig");
        LOGGER.info("[FlywayDigitalAutoConfiguration] Properties: {}", properties);

        FlywayDigitalConfig config = new FlywayDigitalConfig();
        config.setEnabled(properties.isEnabled());
        config.setLocations(properties.getLocations());
        config.setTable(properties.getTable());
        config.setBaselineOnMigrate(properties.isBaselineOnMigrate());
        config.setBaselineVersion(properties.getBaselineVersion());
        config.setValidateOnMigrate(properties.isValidateOnMigrate());
        config.setOutOfOrder(properties.isOutOfOrder());

        return config;
    }

    /**
     * 创建FlywayDigital Bean并执行迁移
     */
    @Bean(initMethod = "migrate")
    @ConditionalOnMissingBean
    @ConditionalOnClass(DataSource.class)
    public FlywayDigital flywayDigital(DataSource dataSource, FlywayDigitalConfig config) throws Exception {
        Objects.requireNonNull(dataSource, "DataSource must not be null");
        Objects.requireNonNull(config, "FlywayDigitalConfig must not be null");

        LOGGER.info("[FlywayDigitalAutoConfiguration] Initializing FlywayDigital");
        LOGGER.info("[FlywayDigitalAutoConfiguration] DataSource: {}", dataSource);
        LOGGER.info("[FlywayDigitalAutoConfiguration] Config: {}", config);

        FlywayDigital flywayDigital = new FlywayDigital(dataSource, config);
        
        // 注意：由于使用了initMethod = "migrate"，Spring会自动调用migrate方法
        // 这里不需要手动调用
        
        return flywayDigital;
    }

}
