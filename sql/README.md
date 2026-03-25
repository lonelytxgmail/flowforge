# SQL Notes

真正的初始化 SQL 目前放在：

- [schema.sql](/Users/lee/LeeProject/flowforge/flowforge-app/src/main/resources/schema.sql)

之所以先放在 Spring `schema.sql`：

- 可以让第一次运行更直接
- 避免一开始引入 Flyway/Liquibase 的额外复杂度
- 等表结构开始频繁演进时，再引入版本化迁移工具
