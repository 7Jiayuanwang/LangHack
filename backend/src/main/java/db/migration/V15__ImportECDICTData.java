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

        // Use the connection provided by Flyway context — avoids duplicate connections
        // and ensures proper transaction boundaries within the migration
        ECDICTImporter importer = new ECDICTImporter(context.getConnection());
        importer.run();

        System.out.println("Flyway: ECDICT import task execution completed.");
    }
}
