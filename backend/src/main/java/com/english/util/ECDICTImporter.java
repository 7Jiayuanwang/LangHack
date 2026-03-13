package com.english.util;

import com.english.entity.ECDICTWord;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * ECDICT数据导入工具
 * 用于一次性将ECDICT CSV文件导入到数据库
 * 
 * 使用方法:
 * java -cp target/classes com.english.util.ECDICTImporter
 * 
 * 或者使用提供的脚本:
 * ./import-ecdict.sh
 */
public class ECDICTImporter {

    private static final String DEFAULT_DB_URL = "jdbc:mysql://localhost:3306/english_learning?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=GMT%2B8&allowPublicKeyRetrieval=true";

    private final String dbUrl;
    private final String dbUser;
    private final String dbPassword;

    public ECDICTImporter(String dbUrl, String dbUser, String dbPassword) {
        this.dbUrl = dbUrl;
        this.dbUser = dbUser;
        this.dbPassword = dbPassword;
    }

    public static void main(String[] args) {
        String dbUrl = System.getenv().getOrDefault("DB_URL", DEFAULT_DB_URL);
        String dbUser = System.getenv().getOrDefault("DB_USER", "root");
        String dbPassword = System.getenv("DB_PASSWORD");
        if (dbPassword == null || dbPassword.isEmpty()) {
            System.err.println("错误: 必须通过环境变量 DB_PASSWORD 提供数据库密码");
            System.err.println("示例: DB_PASSWORD=yourpassword java -cp ... com.english.util.ECDICTImporter");
            System.exit(1);
        }
        ECDICTImporter importer = new ECDICTImporter(dbUrl, dbUser, dbPassword);
        try {
            importer.run();
        } catch (Exception e) {
            System.err.println("导入失败: " + e.getMessage());
            System.err.println("详细错误: " + e.getClass().getName());
            System.exit(1);
        }
    }

    public void run() throws Exception {
        System.out.println("===================================");
        System.out.println("  ECDICT 数据导入工具");
        System.out.println("===================================");
        System.out.println();

        // 加载 MySQL 驱动
        Class.forName("com.mysql.cj.jdbc.Driver");

        try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword)) {
            // 检查是否已有数据
            long existingCount = getWordCount(conn);
            if (existingCount > 0) {
                System.out.println("数据库中已存在 " + existingCount + " 条ECDICT词汇数据");
                System.out.println("如需重新导入，请先执行: TRUNCATE TABLE ecdict_words;");
                System.out.println("导入已取消");
                return;
            }

            // 查找ECDICT文件
            String[] possiblePaths = {
                    "ECDICT-master/ecdict.csv",
                    "../ECDICT-master/ecdict.csv",
                    "/usr/local/ecdict/ecdict.csv"
            };

            File ecdictFile = null;
            for (String path : possiblePaths) {
                File testFile = new File(path);
                if (testFile.exists() && testFile.isFile()) {
                    ecdictFile = testFile;
                    break;
                }
            }

            if (ecdictFile == null) {
                System.err.println("错误: 找不到ECDICT数据文件");
                System.err.println("请确保ECDICT-master/ecdict.csv文件存在");
                return;
            }

            System.out.println("找到ECDICT文件: " + ecdictFile.getAbsolutePath());
            System.out.println("开始导入数据...");
            System.out.println();

            importECDICTData(conn, ecdictFile);

            System.out.println();
            System.out.println("===================================");
            System.out.println("  ECDICT数据导入完成");
            System.out.println("===================================");
            long finalCount = getWordCount(conn);
            System.out.println("数据库中共有 " + finalCount + " 条ECDICT词汇数据");
        }
    }

    private long getWordCount(Connection conn) throws Exception {
        String sql = "SELECT COUNT(*) FROM ecdict_words";
        try (PreparedStatement stmt = conn.prepareStatement(sql);
                ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getLong(1);
            }
            return 0;
        }
    }

    private void importECDICTData(Connection conn, File file) throws IOException {
        int batchSize = 1000;
        List<ECDICTWord> batch = new ArrayList<>();
        int totalCount = 0;
        int successCount = 0;
        int failCount = 0;

        String sql = "INSERT INTO ecdict_words (word, phonetic, definition, translation, pos, collins, oxford, tag, bnc, frq, exchange) "
                +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            String line;
            boolean isFirstLine = true;

            while ((line = reader.readLine()) != null) {
                if (isFirstLine) {
                    isFirstLine = false;
                    continue; // 跳过标题行
                }

                totalCount++;
                ECDICTWord word = parseECDICTLine(line);

                if (word != null && word.getWord() != null && !word.getWord().isEmpty()) {
                    batch.add(word);

                    // 批量插入
                    if (batch.size() >= batchSize) {
                        int inserted = insertBatch(pstmt, batch);
                        successCount += inserted;
                        failCount += (batch.size() - inserted);
                        batch.clear();

                        // 输出进度
                        if (totalCount % 10000 == 0) {
                            System.out.println("已处理 " + totalCount + " 行，成功插入 " + successCount + " 条");
                        }
                    }
                } else {
                    failCount++;
                }
            }

            // 插入剩余的数据
            if (!batch.isEmpty()) {
                int inserted = insertBatch(pstmt, batch);
                successCount += inserted;
                failCount += (batch.size() - inserted);
            }

            System.out.println();
            System.out.println("导入统计:");
            System.out.println("  总行数: " + totalCount);
            System.out.println("  成功: " + successCount);
            System.out.println("  失败: " + failCount);
        } catch (Exception e) {
            throw new IOException("导入数据失败", e);
        }
    }

    private int insertBatch(PreparedStatement pstmt, List<ECDICTWord> words) throws Exception {
        int successCount = 0;
        for (ECDICTWord word : words) {
            try {
                pstmt.setString(1, word.getWord());
                pstmt.setString(2, word.getPhonetic());
                pstmt.setString(3, word.getDefinition());
                pstmt.setString(4, word.getTranslation());
                pstmt.setString(5, word.getPos());
                pstmt.setString(6, word.getCollins());
                pstmt.setString(7, word.getOxford());
                pstmt.setString(8, word.getTag());
                pstmt.setString(9, word.getBnc());
                pstmt.setString(10, word.getFrq());
                pstmt.setString(11, word.getExchange());

                pstmt.addBatch();
                successCount++;
            } catch (Exception e) {
                // 忽略重复键等错误
                String errorMsg = e.getMessage();
                if (errorMsg == null || !errorMsg.contains("Duplicate entry")) {
                    System.err.println("插入失败: " + word.getWord() + " - " + errorMsg);
                }
            }
        }

        // 执行批处理
        try {
            int[] results = pstmt.executeBatch();
            pstmt.clearBatch();
            return successCount;
        } catch (Exception e) {
            System.err.println("批量执行失败: " + e.getMessage());
            pstmt.clearBatch();
            throw e;
        }
    }

    private ECDICTWord parseECDICTLine(String line) {
        if (line == null || line.trim().isEmpty()) {
            return null;
        }

        try {
            List<String> fields = parseCSVLine(line);
            if (fields.size() < 4) {
                return null;
            }

            ECDICTWord word = new ECDICTWord();
            word.setWord(cleanField(fields.get(0)));
            word.setPhonetic(cleanField(fields.get(1)));
            word.setDefinition(cleanField(fields.get(2)));
            word.setTranslation(cleanField(fields.get(3)));

            if (fields.size() > 4)
                word.setPos(cleanField(fields.get(4)));
            if (fields.size() > 5)
                word.setCollins(cleanField(fields.get(5)));
            if (fields.size() > 6)
                word.setOxford(cleanField(fields.get(6)));
            if (fields.size() > 7)
                word.setTag(cleanField(fields.get(7)));
            if (fields.size() > 8)
                word.setBnc(cleanField(fields.get(8)));
            if (fields.size() > 9)
                word.setFrq(cleanField(fields.get(9)));
            if (fields.size() > 10)
                word.setExchange(cleanField(fields.get(10)));

            return word;
        } catch (Exception e) {
            return null;
        }
    }

    private List<String> parseCSVLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder currentField = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    currentField.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                fields.add(currentField.toString());
                currentField.setLength(0);
            } else {
                currentField.append(c);
            }
        }

        fields.add(currentField.toString());
        return fields;
    }

    private String cleanField(String field) {
        if (field == null || field.isEmpty() || field.equals("NULL")) {
            return null;
        }

        field = field.trim();

        if ((field.startsWith("'") && field.endsWith("'")) ||
                (field.startsWith("\"") && field.endsWith("\""))) {
            field = field.substring(1, field.length() - 1);
        }

        // 限制字段长度，避免超出数据库字段限制
        if (field.length() > 1000) {
            // 重点修复：记录截断信息，避免数据无声截断
            System.out.println("警告: 字段已截断（原长度: " + field.length() + "）- " + field.substring(0, Math.min(50, field.length())) + "...");
            field = field.substring(0, 1000);
        }

        return field.isEmpty() ? null : field;
    }
}
