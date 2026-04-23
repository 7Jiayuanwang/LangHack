package db.migration;

import com.english.util.ECDICTImporter;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.ResultSet;

/**
 * Flyway Java Migration: V15__ImportECDICTData
 * Responsible for automatically triggering ECDICT dictionary data import when Flyway starts.
 *
 * Why use Java migration:
 * 1. ECDICT dataset is huge (millions of rows), using SQL scripts would make Flyway run extremely slow
 * 2. Java can leverage JDBC batch insertion (Batch Insert), improving performance hundreds of times
 * 3. Can dynamically locate local file paths
 */
public class V15__ImportECDICTData extends BaseJavaMigration {

    private static final String DEFAULT_DB_URL = "jdbc:mysql://localhost:3306/english_learning?" +
            "useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=GMT%2B8&allowPublicKeyRetrieval=true";

    @Override
    public void migrate(Context context) throws Exception {
        System.out.println("Flyway: Detected ECDICT import task...");

        // Check if ecdict_words table exists — skip if absent (e.g. CI environment)
        try (ResultSet tables = context.getConnection().getMetaData().getTables(null, null, "ecdict_words", null)) {
            if (!tables.next()) {
                System.out.println("Flyway: ecdict_words table not found, skipping ECDICT import.");
                return;
            }
        }

        // Read database credentials from environment variables
        // CRITICAL: DB_PASSWORD must be explicitly set - no hardcoded default for security reasons
        String dbUrl = System.getenv().getOrDefault("DB_URL", DEFAULT_DB_URL);
        String dbUser = System.getenv().getOrDefault("DB_USER", "root");
        String dbPassword = System.getenv("DB_PASSWORD");

        // Validate that password is explicitly provided for security
        if (dbPassword == null || dbPassword.isEmpty()) {
            System.err.println("WARNING: DB_PASSWORD environment variable not set, ECDICT import skipped");
            System.out.println("Flyway: ECDICT import task completed (skipped due to missing credentials).");
            return;
        }

        // Instantiate import tool and run
        ECDICTImporter importer = new ECDICTImporter(dbUrl, dbUser, dbPassword);

        // NOTE: The tool creates its own connection management for now
        // TODO: Refactor ECDICTImporter to accept the Connection provided by Flyway context
        // to avoid duplicate connections and ensure proper transaction boundaries
        importer.run();

        System.out.println("Flyway: ECDICT import task execution completed.");
    }
}
