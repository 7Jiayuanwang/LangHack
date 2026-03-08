# Windows IDE 配置指南

## 🔧 解决 Java 编译错误指南

如果你在Windows的IDE（如IntelliJ IDEA）中遇到 `java.lang.ExceptionInInitializerError com.sun.tools.javac.code.TypeTag :: UNKNOWN` 错误，请按以下步骤配置：

### 1. 确认问题
- IDE显示使用 javac 25.0.1 或其他非Java 17版本
- 项目要求Java 17但IDE使用了其他Java版本
- 命令行编译正常，IDE编译失败

### 2. IntelliJ IDEA 配置

#### 步骤1：设置项目JDK
1. 打开 `File` → `Project Structure` (Ctrl+Alt+Shift+S)
2. 在 `Project` 选项卡中：
   - `Project SDK`: 选择或添加 `Java 17`
   - `Project language level`: 设置为 `17`
   - `Project bytecode version`: 设置为 `17`

#### 步骤2：设置模块编译器版本
1. 在 `Project Structure` 中选择 `Modules` 选项卡
2. 找到 `backend` 模块，确保：
   - `Language level`: `17`
   - `Target bytecode version`: `17`

#### 步骤3：配置Maven设置
1. 在 `Project Structure` 中选择 `Maven` 选项卡
2. 确保使用正确的Maven配置：
   - `Maven home directory`: `D:\Appdizhi\MAVEN\apache-maven-3.9.6`
   - `User settings file`: 默认或你的自定义settings.xml
   - `Local repository`: 默认或自定义仓库路径

#### 步骤4：设置注解处理器
1. 在 `Project Structure` 中选择 `Annotation Processors` 选项卡
2. 勾选 `Enable annotation processing`
3. 设置 `Store generated sources relative to`: `Module content root`
4. 在 `Processor path` 中添加Lombok：
   - 点击 `+` → `JARs or directories...`
   - 选择：`D:\Appdizhi\MAVEN\apache-maven-3.9.6\repository\org\projectlombok\lombok\1.18.30\lombok-1.18.30.jar`

### 3. 清理和重建

#### 方法1：通过IDE菜单
1. `Build` → `Clean Project`
2. `Build` → `Rebuild Project`

#### 方法2：通过Maven
1. 打开 `Maven` 工具窗口 (右侧工具栏)
2. 展开 `Lifecycle`
3. 先执行 `clean`，再执行 `compile`

### 4. 环境变量设置（重要！）

在Windows系统中确保设置了正确的环境变量：

```batch
# 系统环境变量
JAVA_HOME=D:\Appdizhi\JAVA\jdk-17.0.12+7
MAVEN_HOME=D:\Appdizhi\MAVEN\apache-maven-3.9.6
Path=%JAVA_HOME%\bin;%MAVEN_HOME%\bin;%Path%
```

### 5. IDEA的VM选项配置
1. `Help` → `Edit Custom VM Options...`
2. 在打开的idea64.exe.vmoptions文件中确保：
```
-Dfile.encoding=UTF-8
-Duser.timezone=GMT+8
```

### 6. 强制刷新项目
1. `File` → `Invalidate Caches and Restart...`
2. 选择 `Invalidate and Restart`
3. 重启后重新导入项目

### 7. 验证配置
1. 查看IDE底部状态栏的Java版本是否显示17
2. 右键项目根目录 → `Maven` → `Reimport`
3. 执行 `mvn clean compile` 验证编译是否成功

### 8. 其他IDE配置（如Eclipse）

#### Eclipse配置步骤：
1. `Window` → `Preferences` → `Java` → `Installed JREs`
2. 添加Java 17的JDK路径
3. `Window` → `Preferences` → `Java` → `Compiler`
   - `Compiler compliance level`: `17`
   - `Generated .class files compatibility`: `17`
   - `Source compatibility`: `17`

### 9. 如果仍然有问题

#### 检查要点：
1. **确认Java版本**：在命令行运行 `java -version` 确认是否为Java 17
2. **确认Maven版本**：运行 `mvn -version` 确认Maven配置
3. **项目路径**：确保项目路径中没有特殊字符
4. **权限问题**：确保IDE有权限访问Java和Maven目录

#### 最后手段：
如果以上配置都正确但仍有问题，可以：
1. 删除项目根目录的 `.idea` 文件夹
2. 删除 `target` 文件夹
3. 重新用IDE导入项目

## 📞 技术支持

如果按照以上步骤配置后仍有问题，请提供：
1. IDE版本信息
2. Java版本信息 (`java -version`)
3. Maven版本信息 (`mvn -version`)
4. 完整的错误信息
5. 项目截图

---

**注意**：配置完成后，项目应该能正常编译和运行。如果还有编译警告，可以暂时忽略，重点解决编译错误。