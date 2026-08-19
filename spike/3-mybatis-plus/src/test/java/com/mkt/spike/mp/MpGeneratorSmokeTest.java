package com.mkt.spike.mp;

import com.baomidou.mybatisplus.generator.FastAutoGenerator;
import com.baomidou.mybatisplus.generator.config.OutputFile;
import com.baomidou.mybatisplus.generator.engine.VelocityTemplateEngine;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MpGeneratorSmokeTest {

    @Test
    void generateEntityAndMapper() throws Exception {
        String url = "jdbc:mysql://" + env("MYSQL_HOST", "192.168.88.149") + ":"
                + env("MYSQL_PORT", "3308") + "/" + env("MYSQL_DATABASE", "mkt_platform")
                + "?useSSL=false&allowPublicKeyRetrieval=true";
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setUrl(url);
        ds.setUsername(env("MYSQL_USERNAME", "mkt"));
        ds.setPassword(env("MYSQL_PASSWORD", "Mkt_2026_88x149"));
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS spike_mp_demo (
                  id BIGINT PRIMARY KEY,
                  name VARCHAR(64) NOT NULL,
                  version INT NOT NULL DEFAULT 0
                )
                """);
        Path out = Files.createTempDirectory("mp-gen");
        FastAutoGenerator.create(url, env("MYSQL_USERNAME", "mkt"), env("MYSQL_PASSWORD", "Mkt_2026_88x149"))
                .globalConfig(builder -> builder.author("spike").outputDir(out.resolve("src").toString()).disableOpenDir())
                .packageConfig(builder -> builder.parent("com.mkt.spike.mp.gen")
                        .pathInfo(Collections.singletonMap(OutputFile.xml, out.resolve("mapper").toString())))
                .strategyConfig(builder -> builder.addInclude("spike_mp_demo")
                        .entityBuilder()
                        .mapperBuilder().enableBaseResultMap())
                .templateEngine(new VelocityTemplateEngine())
                .execute();
        jdbc.execute("DROP TABLE IF EXISTS spike_mp_demo");
        assertTrue(Files.walk(out).anyMatch(p -> p.getFileName().toString().endsWith(".java")));
    }

    private static String env(String key, String fallback) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? fallback : v;
    }
}
