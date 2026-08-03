package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.SQLException;

/**
 * Align legacy {@code uav_device} with domain table naming.
 * No-op when the table was already created as {@code device} (V5 / MySQL init).
 * Portable across MySQL (prod) and H2 (local/CI).
 */
public class V6__RenameUavDeviceToDevice extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        boolean hasOld = tableExists(connection, "uav_device");
        boolean hasNew = tableExists(connection, "device");

        if (!hasOld) {
            return;
        }

        try (Statement statement = connection.createStatement()) {
            if (!hasNew) {
                renameTable(statement, connection, "uav_device", "device");
                return;
            }

            statement.execute("""
                    INSERT INTO device (device_code, device_name, device_type, status, created_at, updated_at)
                    SELECT device_code, device_name, device_type, status, created_at, updated_at
                    FROM uav_device src
                    WHERE NOT EXISTS (
                        SELECT 1 FROM device dst WHERE dst.device_code = src.device_code
                    )
                    """);
            statement.execute("DROP TABLE uav_device");
        }
    }

    private static void renameTable(
            Statement statement,
            Connection connection,
            String from,
            String to) throws SQLException {
        String product = connection.getMetaData().getDatabaseProductName();
        if (product != null && product.toLowerCase().contains("mysql")) {
            statement.execute("RENAME TABLE " + from + " TO " + to);
        } else {
            statement.execute("ALTER TABLE " + from + " RENAME TO " + to);
        }
    }

    private static boolean tableExists(Connection connection, String tableName) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        String catalog = connection.getCatalog();
        String[] candidates = {
                tableName,
                tableName.toLowerCase(),
                tableName.toUpperCase()
        };
        for (String candidate : candidates) {
            try (ResultSet tables = metaData.getTables(catalog, null, candidate, new String[]{"TABLE"})) {
                if (tables.next()) {
                    return true;
                }
            }
            try (ResultSet tables = metaData.getTables(null, null, candidate, new String[]{"TABLE"})) {
                if (tables.next()) {
                    return true;
                }
            }
        }
        return false;
    }
}
