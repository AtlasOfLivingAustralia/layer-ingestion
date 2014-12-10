package au.org.ala.layers.ingestion;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/*
 * Used to sync the production database with the development database. Overwrites the database
 *  with a dump file. Ensures that the enabled/disabled states of layers and fields remains the
 *  same as in the old version of the production database. Any newly added layers or fields are disabled
 *  by default
 *  
 *  NOTE - assumes that the postgresql tools pg_dump, pg_restore, createdb and dropdb are available on the system path.
 *  Also assumes that the database user "postgres" exists.
 */
public class DatabaseSync {

    /**
     * @param args
     */
    public static void main(String[] args) {
        String dbUsername = args[0];
        String dbPassword = args[1];
        String dbJdbcUrl = args[2];
        String dbName = args[3];
        String dumpFilePath = args[4];
        String backupDumpFilePath = args[5];

        if (args.length != 6) {
            System.out.println("Usage: DatabaseSync dbUsername dbPassword dbJdbcUrl dbName dumpFilePath backupDumpFilePath");
            // Abnormal termination
            System.exit(1);
        }

        try {

            File dumpFile = new File(dumpFilePath);
            if (!dumpFile.exists()) {
                throw new RuntimeException(String.format("Dump file %s does not exist", dumpFile.getAbsolutePath()));
            }

            File backupDumpFile = new File(backupDumpFilePath);

            // backup the old database
            System.out.println("Backing up the database");
            Process pg_dump = Runtime.getRuntime().exec(new String[]{"pg_dump", "-Fc", "-U", "postgres", "-f", backupDumpFile.getAbsolutePath(), dbName});
            int pgDumpReturnVal = pg_dump.waitFor();

            if (pgDumpReturnVal != 0) {
                String pgDumpErrorOutput = IOUtils.toString(pg_dump.getErrorStream());
                throw new RuntimeException("Database backup failed: " + pgDumpErrorOutput);
            }

            // Connect to the database
            System.out.println("Connecting to database");
            Class.forName("org.postgresql.Driver");
            Properties props = new Properties();
            props.setProperty("user", dbUsername);
            props.setProperty("password", dbPassword);
            Connection conn = DriverManager.getConnection(dbJdbcUrl, props);
            conn.setAutoCommit(false);

            // get enabled layers
            System.out.println("Getting enabled layers");
            List<Integer> enabledLayers = new ArrayList<Integer>();
            PreparedStatement stGetEnabledLayers = conn.prepareStatement("SELECT id from layers where enabled = TRUE;");
            ResultSet rsEnabledLayers = stGetEnabledLayers.executeQuery();
            while (rsEnabledLayers.next()) {
                enabledLayers.add(rsEnabledLayers.getInt(1));
            }

            // get enabled fields
            System.out.println("Getting enabled fields");
            List<String> enabledFields = new ArrayList<String>();
            PreparedStatement stGetEnabledFields = conn.prepareStatement("SELECT id from fields where enabled = TRUE;");
            ResultSet rsEnabledFields = stGetEnabledFields.executeQuery();
            while (rsEnabledFields.next()) {
                enabledFields.add(rsEnabledFields.getString(1));
            }

            // Close the connection, as we will be dropping this database
            System.out.println("Closing database connection");
            conn.close();

            // drop the database
            System.out.println("Drop the database");
            Process dropDB = Runtime.getRuntime().exec(new String[]{"dropdb", "-U", "postgres", dbName});
            int dropDBReturnVal = dropDB.waitFor();

            if (dropDBReturnVal != 0) {
                String dropDBErrorOutput = IOUtils.toString(dropDB.getErrorStream());
                throw new RuntimeException("Database drop failed: " + dropDBErrorOutput);
            }

            // create a new version of the database
            System.out.println("Creating new blank database");
            Process createDB = Runtime.getRuntime().exec(new String[]{"createdb", "-U", "postgres", dbName});
            int createDBReturnVal = createDB.waitFor();

            if (createDBReturnVal != 0) {
                String createDBErrorOutput = IOUtils.toString(createDB.getErrorStream());
                throw new RuntimeException("Database drop failed: " + createDBErrorOutput);
            }

            // restore from the new database dump file
            System.out.println("Loading new version of database using pg_restore");
            Process pg_restore = Runtime.getRuntime().exec(new String[]{"pg_restore", "-d", dbName, "-U", "postgres", dumpFile.getAbsolutePath()});
            int pgRestoreReturnVal = pg_restore.waitFor();

            if (pgRestoreReturnVal != 0) {
                String pgRestoreErrorOutput = IOUtils.toString(pg_restore.getErrorStream());
                throw new RuntimeException("pg_restore failed: " + pgRestoreErrorOutput);
            }

            // Reconnect to the database
            // Connect to the database
            System.out.println("Connecting to new database");
            Class.forName("org.postgresql.Driver");
            Properties props2 = new Properties();
            props2.setProperty("user", dbUsername);
            props2.setProperty("password", dbPassword);
            Connection conn2 = DriverManager.getConnection(dbJdbcUrl, props2);
            conn2.setAutoCommit(false);

            // set all layers disabled (all new layers are disabled)
            System.out.println("Disabling all layers");
            PreparedStatement stDisableAllLayers = conn2.prepareStatement("UPDATE layers SET enabled = FALSE where true;");
            stDisableAllLayers.executeUpdate();

            // set all fields disabled (all new fields are disabled)
            System.out.println("Disabling all fields");
            PreparedStatement stDisableAllFields = conn2.prepareStatement("UPDATE fields SET enabled = FALSE where true;");
            stDisableAllFields.executeUpdate();

            // enable previously enabled layers
            System.out.println("Enabling layers that were enabled in old database");
            PreparedStatement stEnableLayer = conn2.prepareStatement("UPDATE layers SET enabled = TRUE where id = ?;");
            for (int enabledLayerId : enabledLayers) {
                stEnableLayer.setInt(1, enabledLayerId);
                stEnableLayer.executeUpdate();
            }

            // enable previously enabled fields
            System.out.println("Enabling fields that were enabled in old database");
            PreparedStatement stEnableField = conn2.prepareStatement("UPDATE fields SET enabled = TRUE where id = ?;");
            for (String enabledFieldId : enabledFields) {
                stEnableField.setString(1, enabledFieldId);
                stEnableField.executeUpdate();
            }

            conn2.commit();
        } catch (Exception ex) {
            ex.printStackTrace();
            // indicate abnormal termination
            System.exit(1);
        }
    }

}
