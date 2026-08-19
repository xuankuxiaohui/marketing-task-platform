package com.mkt.risk.it;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import com.mkt.risk.application.MybatisRiskHandleLogStore;
import com.mkt.risk.application.MybatisRiskHitLogStore;
import com.mkt.risk.application.MybatisRiskListItemStore;
import com.mkt.risk.application.MybatisRiskRuleConfigStore;
import com.mkt.risk.application.RiskHandleLogStore;
import com.mkt.risk.application.RiskHitLogStore;
import com.mkt.risk.application.RiskListItemStore;
import com.mkt.risk.application.RiskRuleConfigStore;
import com.mkt.risk.mapper.RiskHandleLogMapper;
import com.mkt.risk.mapper.RiskHitLogMapper;
import com.mkt.risk.mapper.RiskListItemMapper;
import com.mkt.risk.mapper.RiskRuleConfigMapper;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/** Production MyBatis-Plus mappers against the IT datasource. */
final class RiskMybatisStores {

    final RiskListItemStore lists;
    final RiskHitLogStore hits;
    final RiskHandleLogStore handles;
    final RiskRuleConfigStore rules;

    private RiskMybatisStores(
            RiskListItemStore lists,
            RiskHitLogStore hits,
            RiskHandleLogStore handles,
            RiskRuleConfigStore rules) {
        this.lists = lists;
        this.hits = hits;
        this.handles = handles;
        this.rules = rules;
    }

    static RiskMybatisStores create(DataSource dataSource) {
        try {
            MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
            factoryBean.setDataSource(dataSource);
            factoryBean.setTransactionFactory(new SpringManagedTransactionFactory());
            factoryBean.setMapperLocations(new PathMatchingResourcePatternResolver()
                    .getResources("classpath*:mapper/risk/*.xml"));
            factoryBean.setTypeAliasesPackage("com.mkt.risk.entity");
            MybatisConfiguration configuration = new MybatisConfiguration();
            configuration.setMapUnderscoreToCamelCase(true);
            configuration.addMapper(RiskListItemMapper.class);
            configuration.addMapper(RiskHitLogMapper.class);
            configuration.addMapper(RiskHandleLogMapper.class);
            configuration.addMapper(RiskRuleConfigMapper.class);
            factoryBean.setConfiguration(configuration);
            GlobalConfig globalConfig = new GlobalConfig();
            GlobalConfig.DbConfig dbConfig = new GlobalConfig.DbConfig();
            dbConfig.setIdType(IdType.AUTO);
            globalConfig.setDbConfig(dbConfig);
            factoryBean.setGlobalConfig(globalConfig);
            SqlSessionFactory factory = factoryBean.getObject();
            if (factory == null) {
                throw new IllegalStateException("SqlSessionFactory is null");
            }
            SqlSessionTemplate sqlSession = new SqlSessionTemplate(factory);
            return new RiskMybatisStores(
                    new MybatisRiskListItemStore(sqlSession.getMapper(RiskListItemMapper.class)),
                    new MybatisRiskHitLogStore(sqlSession.getMapper(RiskHitLogMapper.class)),
                    new MybatisRiskHandleLogStore(sqlSession.getMapper(RiskHandleLogMapper.class)),
                    new MybatisRiskRuleConfigStore(sqlSession.getMapper(RiskRuleConfigMapper.class)));
        } catch (Exception ex) {
            throw new IllegalStateException("risk mybatis factory", ex);
        }
    }
}
