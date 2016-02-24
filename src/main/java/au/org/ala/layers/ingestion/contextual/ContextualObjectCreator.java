package au.org.ala.layers.ingestion.contextual;

import au.org.ala.layers.ingestion.IngestionUtils;
import au.org.ala.layers.stats.ObjectsStatsGenerator;

import java.sql.*;
import java.text.MessageFormat;
import java.util.Properties;

public class ContextualObjectCreator {

    public static void main(String[] args) {
        if (args.length < 4) {
            System.out.println("Usage: layerId dbUsername dbPassword dbJdbcUrl");
            System.exit(1);
        }

        int layerId = Integer.parseInt(args[0]);
        String dbUsername = args[1];
        String dbPassword = args[2];
        String dbJdbcUrl = args[3];

        try {
            boolean success = create(layerId, dbUsername, dbPassword, dbJdbcUrl);
            if (!success) {
                System.exit(1);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public static boolean create(int layerId, String dbUsername, String dbPassword, String dbJdbcUrl) throws Exception {
        System.out.println("Connecting to database");
        Class.forName("org.postgresql.Driver");
        Properties props = new Properties();
        props.setProperty("user", dbUsername);
        props.setProperty("password", dbPassword);
        Connection conn = DriverManager.getConnection(dbJdbcUrl, props);
        conn.setAutoCommit(false);

        try {
            // Read field sid, name and description from fields table for any
            // fields that do not have objects created for them.
            System.out.println("Reading field sid, name and description...");
            PreparedStatement fieldDetailsSelect = conn.prepareStatement("SELECT id, sid, sname, sdesc, namesearch FROM fields where spid = ? AND id NOT in (SELECT DISTINCT fid from objects)");
            fieldDetailsSelect.setString(1, Integer.toString(layerId));
            ResultSet rs = fieldDetailsSelect.executeQuery();

            int numberFieldsTableEntriesProcessed = 0;

            while (rs.next()) {
                String fieldId = rs.getString(1);
                String fieldsSid = rs.getString(2);
                String fieldsSname = rs.getString(3);
                String fieldsSdesc = rs.getString(4);
                boolean namesearch = rs.getBoolean(5);

                // insert to objects table
                System.out.println("Creating objects table entries...");
                PreparedStatement createObjectsStatement = createObjectsInsert(conn, layerId, fieldId, fieldsSid, fieldsSname, fieldsSdesc, namesearch);
                createObjectsStatement.execute();

                numberFieldsTableEntriesProcessed++;
            }

            if (numberFieldsTableEntriesProcessed == 0) {
                throw new RuntimeException("No fields table entry for layer, or objects have already been created for all fields table entries for layer.");
            }

            // generate object names
            System.out.println("Generating object names...");
            PreparedStatement createObjectNamesStatement = IngestionUtils.createObjectNameGenerationStatement(conn);
            createObjectNamesStatement.execute();

            // generate object bboxes and areas
            System.out.println("Generating object bounding boxes and areas...");
            PreparedStatement createBBoxesAndAreaStatement = IngestionUtils.createGenerateObjectsBBoxAndAreaStatement(conn);
            createBBoxesAndAreaStatement.execute();

            // ObjectStatsGenerator is dependent on data inserted above, and
            // creates its own connection to the database, so
            // need to commit here.
            conn.commit();
        } catch (Exception ex) {
            ex.printStackTrace();
            return false;
        }

        // Run the object stats generator tool to do further operations on
        // bboxes and areas
        System.out.println("Running ObjectStatsGenerator tool...");
        try {
            ObjectsStatsGenerator.main(new String[]{"10", dbJdbcUrl, dbUsername, dbPassword});
        } catch (Exception ex) {
            ex.printStackTrace();
            return false;
        }

        return true;
    }

    private static PreparedStatement createObjectsInsert(Connection conn, int layerId, String fieldId, String fieldsSid,
                                                         String fieldsSname, String fieldsSdesc, boolean namesearch) throws SQLException {
        // Unfortunately table and column names can't be substituted with
        // PreparedStatements, so we have to hardcode them
        PreparedStatement stLayersInsert = conn.prepareStatement(MessageFormat.format("INSERT INTO objects (pid, id, name, \"desc\", fid, the_geom, namesearch)"
                        + " SELECT nextval(''objects_id_seq''::regclass), {0}, MAX({1}), MAX({2}), ''{3}'', ST_UNION(the_geom), {4} FROM \"{5}\" GROUP BY {6}", fieldsSid, fieldsSname,
                fieldsSdesc == null ? "NULL" : fieldsSdesc, fieldId, Boolean.toString(namesearch), Integer.toString(layerId), fieldsSid));
        return stLayersInsert;
    }
}
