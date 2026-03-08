package db.migration;

import com.english.util.ECDICTImporter;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import java.sql.Connection;

/**
 * Flyway Java 迁移类：V4__ImportECDICTData
 * 负责在 Flyway 启动时自动触发 ECDICT 词典数据的导入。
 * 
 * 为什么使用 Java 迁移：
 * 1. ECDICT 数据量巨大（几百万行），使用 SQL 脚本会导致 Flyway 运行极慢。
 * 2. Java 可以利用 JDBC 批量插入（Batch Insert），性能提升数百倍。
 * 3. 可以动态查找本地文件路径。
 */
public class V4__ImportECDICTData extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        
        System.out.println("Flyway: 检测到 ECDICT 导入任务...");
        
        // 实例化导入工具并运行
        String dbUrl = System.getenv().getOrDefault("DB_URL",
                "jdbc:mysql://localhost:3306/english_learning?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=GMT%2B8&allowPublicKeyRetrieval=true");
        String dbUser = System.getenv().getOrDefault("DB_USER", "root");
        String dbPassword = System.getenv().getOrDefault("DB_PASSWORD", "");
        ECDICTImporter importer = new ECDICTImporter(dbUrl, dbUser, dbPassword);
        
        // 我们重用了现有的逻辑，它会自动检查表中是否有数据
        // 注意：由于 Flyway 已经提供了 Connection，我们理想情况下应该重构 Importer 以接收该连接
        // 但为了最小化改动，我们让 Importer 使用自己的连接管理
        importer.run();
        
        System.out.println("Flyway: ECDICT 导入流程执行完毕。");
    }
}
