package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Ensure {@code inspection_task.route_code} exists for schemas created before Sprint C.
 * Skips when the table is absent (H2/local: Hibernate ddl-auto creates it) or the column
 * already exists (fresh MySQL init).
 */
public class V8__InspectionTaskRouteCode extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        if (!tableExists(connection, "inspection_task")) {
            return;
        }
        if (columnExists(connection, "inspection_task", "route_code")) {
            return;
        }

        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE inspection_task ADD COLUMN route_code VARCHAR(64) NULL");
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

    private static boolean columnExists(Connection connection, String tableName, String columnName)
            throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        String catalog = connection.getCatalog();
        String[] tableCandidates = {
                tableName,
                tableName.toLowerCase(),
                tableName.toUpperCase()
        };
        String[] columnCandidates = {
                columnName,
                columnName.toLowerCase(),
                columnName.toUpperCase()
        };
        for (String tableCandidate : tableCandidates) {
            for (String columnCandidate : columnCandidates) {
                try (ResultSet columns = metaData.getColumns(catalog, null, tableCandidate, columnCandidate)) {
                    if (columns.next()) {
                        return true;
                    }
                }
                try (ResultSet columns = metaData.getColumns(null, null, tableCandidate, columnCandidate)) {
                    if (columns.next()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
