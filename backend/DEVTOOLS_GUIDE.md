# Spring Boot DevTools 配置说明

## 已完成的配置

### 1. 添加依赖
在 `pom.xml` 中已添加 Spring Boot DevTools 依赖：
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-devtools</artifactId>
    <scope>runtime</scope>
    <optional>true</optional>
</dependency>
```

### 2. 配置文件
在 `application.yml` 中已添加 DevTools 配置：
```yaml
spring:
  devtools:
    restart:
      enabled: true # 启用自动重启
      additional-paths: src/main/java # 监控的路径
      exclude: static/**,public/**,templates/** # 排除不需要重启的资源
      poll-interval: 1000 # 轮询间隔（毫秒）
      quiet-period: 400 # 静默期（毫秒），避免频繁重启
    livereload:
      enabled: false # 禁用LiveReload（因为前端是独立的）
```

### 3. Maven 插件配置
在 `pom.xml` 中已配置 spring-boot-maven-plugin：
```xml
<plugin>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-maven-plugin</artifactId>
    <configuration>
        <addResources>true</addResources> <!-- 允许资源热加载 -->
    </configuration>
</plugin>
```

## 使用方法

### 方式一：使用 Maven（推荐）

1. **启动应用**：
   ```bash
   source env.sh
   cd backend
   mvn spring-boot:run
   ```

2. **修改代码后**：
   - **Java 文件**：保存后，Maven 会自动编译，DevTools 会检测到 class 文件的变化并自动重启
   - **配置文件**：保存后会自动重新加载

3. **触发重启**：
   - 在 IDE 中保存文件后，IDE 通常会自动编译
   - 或者手动运行：`mvn compile` 来触发编译

### 方式二：使用 IDE（IntelliJ IDEA）

1. **配置 IDEA**：
   - 打开 `Settings` → `Build, Execution, Deployment` → `Compiler`
   - 勾选 `Build project automatically`
   - 按 `Ctrl+Shift+A`（Mac: `Cmd+Shift+A`），搜索 `Registry`
   - 找到 `compiler.automake.allow.when.app.running` 并勾选

2. **启动应用**：
   - 右键点击 `EnglishLearningPlatformApplication.java`
   - 选择 `Run` 或 `Debug`

3. **修改代码后**：
   - 保存文件（`Ctrl+S` / `Cmd+S`）
   - IDEA 会自动编译，DevTools 会自动重启应用

### 方式三：使用 VS Code

1. **安装插件**：
   - 安装 `Java Extension Pack`
   - 安装 `Spring Boot Extension Pack`

2. **配置自动编译**：
   在 `.vscode/settings.json` 中添加：
   ```json
   {
     "java.autobuild.enabled": true,
     "java.compile.nullAnalysis.mode": "automatic"
   }
   ```

3. **启动应用**：
   - 打开 `EnglishLearningPlatformApplication.java`
   - 点击 `Run` 按钮或按 `F5`

4. **修改代码后**：
   - 保存文件会自动编译和重启

## 验证 DevTools 是否工作

### 测试步骤：

1. **启动应用**：
   ```bash
   source env.sh
   cd backend
   mvn spring-boot:run
   ```

2. **访问测试接口**：
   ```bash
   curl http://localhost:8080/api/test/devtools
   ```
   应该返回：`DevTools is working! Version 1`

3. **修改测试文件**：
   编辑 `backend/src/main/java/com/english/test/DevToolsTestController.java`
   将 `Version 1` 改为 `Version 2`

4. **保存并编译**：
   ```bash
   cd backend
   mvn compile
   ```

5. **等待几秒后再次访问**：
   ```bash
   curl http://localhost:8080/api/test/devtools
   ```
   应该返回：`DevTools is working! Version 2`

6. **检查日志**：
   在控制台中应该能看到类似这样的日志：
   ```
   Restarting due to 1 class path change
   ```

## 注意事项

1. **DevTools 只在开发环境生效**：
   - 当应用以 `java -jar` 方式运行时，DevTools 会自动禁用
   - 生产环境不会包含 DevTools

2. **重启速度**：
   - DevTools 使用两个类加载器：base classloader 和 restart classloader
   - 只有你修改的类会被重新加载，所以重启很快（通常 1-3 秒）

3. **不会重启的情况**：
   - 修改 `pom.xml` 需要完全重启 Maven
   - 修改某些配置文件可能需要完全重启

4. **排除不需要监控的文件**：
   - 已配置排除 `static/**`, `public/**`, `templates/**`
   - 这些资源的修改不会触发重启

## 常见问题

### Q: 修改代码后没有自动重启？
A: 确保：
- 文件已保存
- IDE 已自动编译（或手动运行 `mvn compile`）
- 检查 `application.yml` 中 `spring.devtools.restart.enabled` 是否为 `true`

### Q: 重启太频繁？
A: 调整 `application.yml` 中的参数：
```yaml
spring:
  devtools:
    restart:
      poll-interval: 2000  # 增加轮询间隔
      quiet-period: 1000   # 增加静默期
```

### Q: 想要禁用 DevTools？
A: 在 `application.yml` 中设置：
```yaml
spring:
  devtools:
    restart:
      enabled: false
```

## 总结

✅ DevTools 已配置完成
✅ 修改代码后会自动重启（需要先编译）
✅ 大大提高开发效率，无需手动重启服务

现在你可以愉快地开发了！每次修改代码保存后，应用会自动重启，通常只需要 1-3 秒。
