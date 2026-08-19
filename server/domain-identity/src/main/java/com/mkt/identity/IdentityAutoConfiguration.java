package com.mkt.identity;

import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@ConditionalOnBean(DataSource.class)
@Import(IdentityPersistenceScan.class)
public class IdentityAutoConfiguration {}
