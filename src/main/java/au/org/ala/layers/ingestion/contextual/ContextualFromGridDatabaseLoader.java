package au.org.ala.layers.ingestion.contextual;

import au.org.ala.layers.ingestion.IngestionUtils;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.text.MessageFormat;
import java.util.Properties;

public class ContextualFromGridDatabaseLoader {

    public static final String CLASSES_FIELD_SUFFIX = "a";
    public static final String INDIVIDUAL_OBJECTS_FIELD_SUFFIX = "b";

    /**
     * @param args
     */
    public static void main(String[] args) {
        if (args.length < 7) {
            System.out.println("USAGE: layerId layerName layerDescription divaGrdFile dbUsername dbPassword dbJdbcUrl");
            System.exit(1); // Abnormal termination
        }

        int layerId = Integer.parseInt(args[0]);
        String layerName = args[1];
        String layerDescription = args[2];
        File divaGrdFile = new File(args[3]);
        String dbUsername = args[4];
        String dbPassword = args[5];
        String dbJdbcUrl = args[6];

        try {
            boolean success = create(layerId, layerName, layerDescription, divaGrdFile, dbUsername, dbPassword, dbJdbcUrl);
            if (!success) {
                // Abnormal termination
                System.exit(1);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            // Abnormal termination
            System.exit(1);
        }
    }

    public static boolean create(int layerId, String layerName, String layerDescription, File divaGrdFile, String dbUsername, String dbPassword, String dbJdbcUrl) throws Exception {
        System.out.println("Beginning environmetal load");

        System.out.println("Connecting to database");
        Class.forName("org.postgresql.Driver");
        Properties props = new Properties();
        props.setProperty("user", dbUsername);
        props.setProperty("password", dbPassword);
        Connection conn = DriverManager.getConnection(dbJdbcUrl, props);
        conn.setAutoCommit(false);

        try {
            // read extents, min/max from diva .grd file
            System.out.println("Extracting extents and min/max environmental value from diva .grd file");
            if (!divaGrdFile.exists()) {
                throw new RuntimeException("Could not locate diva .grd file: " + divaGrdFile.toString());
            }

            String strDivaGrd = FileUtils.readFileToString(divaGrdFile);

            float minLatitude = Float.parseFloat(IngestionUtils.matchPattern(strDivaGrd, "^MinY=(.+)$"));
            float maxLatitude = Float.parseFloat(IngestionUtils.matchPattern(strDivaGrd, "^MaxY=(.+)$"));
            float minLongitude = Float.parseFloat(IngestionUtils.matchPattern(strDivaGrd, "^MinX=(.+)$"));
            float maxLongitude = Float.parseFloat(IngestionUtils.matchPattern(strDivaGrd, "^MaxX=(.+)$"));

            // insert to layers table
            String displayPath = MessageFormat.format(IngestionUtils.GEOSERVER_QUERY_TEMPLATE, layerName);
            System.out.println("Creating layers table entry...");

            PreparedStatement createLayersStatement = IngestionUtils.createLayersInsertForContextual(conn, layerId, layerDescription, divaGrdFile.getParentFile().getAbsolutePath(), layerName,
                    displayPath, minLatitude, minLongitude, maxLatitude, maxLongitude, "shape_diva/" + layerName);
            createLayersStatement.execute();

            // create fields table entry for class objects
            System.out.println("Creating fields table entry for class objects...");
            String classFieldId = IngestionUtils.CONTEXTUAL_FIELD_PREFIX + Integer.toString(layerId);
            PreparedStatement createClassesFieldStatement = IngestionUtils.createFieldsInsert(conn, layerId, layerDescription, layerDescription, classFieldId,
                    IngestionUtils.CONTEXTUAL_FROM_GRID_CLASSES_FIELD_TYPE, null, null, null, true, true, true, true, true, false, true, true);
            createClassesFieldStatement.execute();

        } catch (Exception ex) {
            ex.printStackTrace();
            conn.rollback();
            return false;
        }

        conn.commit();
        return true;
    }
}
