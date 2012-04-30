package au.org.ala.layers.ingestion.contextual;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.Properties;

import org.ala.layers.stats.ObjectsStatsGenerator;

public class ContextualObjectCreator {

    public static void main(String[] args) {

    }

    public static void create(int layerId, String fieldsSid, String fieldsSname, String fieldsSdesc, String dbUsername, String dbPassword, String dbJdbcUrl) throws Exception {
        System.out.println("Connecting to database");
        Class.forName("org.postgresql.Driver");
        Properties props = new Properties();
        props.setProperty("user", dbUsername);
        props.setProperty("password", dbPassword);
        Connection conn = DriverManager.getConnection(dbJdbcUrl, props);
        conn.setAutoCommit(false);

        try {
            // insert to objects table
            System.out.println("Creating objects table entries...");
            PreparedStatement createObjectsStatement = createObjectsInsert(conn, layerId, fieldsSid, fieldsSname, fieldsSdesc);
            createObjectsStatement.execute();

            // generate object names
            System.out.println("Generating object names...");
            PreparedStatement createObjectNamesStatement = createObjectNameGenerationStatement(conn);
            createObjectNamesStatement.execute();

            // generate object bboxes and areas
            System.out.println("Generating object bounding boxes and areas...");
            PreparedStatement createBBoxesAndAreaStatement = createGenerateObjectsBBoxAndAreaStatement(conn);
            createBBoxesAndAreaStatement.execute();

            // ObjectStatsGenerator is dependent on data inserted above, and
            // creates its own connection to the database, so
            // need to commit here.
            conn.commit();
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        // Run the object stats generator tool to do further operations on
        // bboxes and areas
        System.out.println("Running ObjectStatsGenerator tool...");
        try {
            ObjectsStatsGenerator.main(new String[] { "10", dbJdbcUrl, dbUsername, dbPassword });
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private static PreparedStatement createObjectsInsert(Connection conn, int layerId, String fieldsSid, String fieldsSname, String fieldsSdesc) throws SQLException {
        // Unfortunately table and column names can't be substituted with
        // PreparedStatements, so we have to hardcode them
        PreparedStatement stLayersInsert = conn.prepareStatement(MessageFormat.format("INSERT INTO objects (pid, id, name, \"desc\", fid, the_geom, namesearch)"
                + " SELECT nextval(''objects_id_seq''::regclass), {0}, MAX({1}), MAX({2}), ''{3}'', ST_UNION(the_geom), TRUE FROM \"{4}\" GROUP BY {5}", fieldsSid, fieldsSname,
                fieldsSdesc == null ? "NULL" : fieldsSdesc, "cl" + Integer.toString(layerId), layerId, fieldsSid));
        return stLayersInsert;
    }

    private static PreparedStatement createObjectNameGenerationStatement(Connection conn) throws SQLException {
        PreparedStatement objectNameGenerationStatement = conn.prepareStatement("INSERT INTO obj_names (name)" + "  SELECT lower(objects.name) FROM fields, objects"
                + "  LEFT OUTER JOIN obj_names ON lower(objects.name)=obj_names.name" + "  WHERE obj_names.name IS NULL" + "  AND fields.namesearch = true" + " AND fields.id = objects.fid"
                + " GROUP BY lower(objects.name);" + "  UPDATE objects SET name_id=obj_names.id FROM obj_names WHERE name_id IS NULL AND lower(objects.name)=obj_names.name;");

        return objectNameGenerationStatement;
    }

    private static PreparedStatement createGenerateObjectsBBoxAndAreaStatement(Connection conn) throws SQLException {
        PreparedStatement generateBBoxAndAreaStatement = conn.prepareStatement("update objects set bbox = ST_AsText(Box2D(the_geom)) where bbox is null; "
                + "update objects set area_km=0 where st_geometrytype(the_geom) = 'ST_Point';");

        return generateBBoxAndAreaStatement;
    }
}
