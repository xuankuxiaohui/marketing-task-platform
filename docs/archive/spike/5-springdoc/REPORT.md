# Spike 5 · springdoc-openapi

- **结论：采用** `org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.0`
- **Boot 4：** `AutoConfigureMockMvc` 旧包不在 starter-test 里，本 spike 用 RANDOM_PORT + `RestClient`
- **分组：** `admin` / `portal` / `internal` 路径不串组
- **关 UI：** `springdoc.swagger-ui.enabled=false` → `/swagger-ui/index.html` 4xx

## 复现

```
set JAVA_HOME=D:\develop\jdk\jdk-26.0.2
mvn -f spike/5-springdoc/pom.xml test
```
