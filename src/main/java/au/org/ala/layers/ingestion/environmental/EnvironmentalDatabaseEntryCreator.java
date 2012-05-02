package au.org.ala.layers.ingestion.environmental;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.text.MessageFormat;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;

public class EnvironmentalDatabaseEntryCreator {

    private static final String GEOSERVER_QUERY_TEMPLATE = "<COMMON_GEOSERVER_URL>/gwc/service/wms?service=WMS&version=1.1.0&request=GetMap&layers=ALA:{0}&format=image/png&styles=";

    /**
     * @param args
     */
    public static void main(String[] args) {
        if (args.length < 8) {
            System.out.println("USAGE: layerId layerName layerDescription units divaGrdFile dbUsername dbPassword dbJdbcUrl");
            System.exit(1); // Abnormal termination
        }

        int layerId = Integer.parseInt(args[0]);
        String layerName = args[1];
        String layerDescription = args[2];
        String units = args[3];
        File divaGrdFile = new File(args[4]);
        String dbUsername = args[5];
        String dbPassword = args[6];
        String dbJdbcUrl = args[7];

        try {
            boolean success = create(layerId, layerName, layerDescription, units, divaGrdFile, dbUsername, dbPassword, dbJdbcUrl);
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

    public static boolean create(int layerId, String layerName, String layerDescription, String units, File divaGrdFile, String dbUsername, String dbPassword, String dbJdbcUrl) throws Exception {
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

            float minValue = Float.parseFloat(matchPattern(strDivaGrd, "^MinValue=(.+)$"));
            float maxValue = Float.parseFloat(matchPattern(strDivaGrd, "^MaxValue=(.+)$"));
            float minLatitude = Float.parseFloat(matchPattern(strDivaGrd, "^MinY=(.+)$"));
            float maxLatitude = Float.parseFloat(matchPattern(strDivaGrd, "^MaxY=(.+)$"));
            float minLongitude = Float.parseFloat(matchPattern(strDivaGrd, "^MinX=(.+)$"));
            float maxLongitude = Float.parseFloat(matchPattern(strDivaGrd, "^MaxX=(.+)$"));

            // insert to layers table
            String displayPath = MessageFormat.format(GEOSERVER_QUERY_TEMPLATE, layerName);
            System.out.println("Creating layers table entry...");
            PreparedStatement createLayersStatement = createLayersInsert(conn, layerId, layerDescription, divaGrdFile.getParentFile().getAbsolutePath(), layerName, displayPath, minLatitude,
                    minLongitude, maxLatitude, maxLongitude, minValue, maxValue, units);
            createLayersStatement.execute();

            // insert to fields table
            System.out.println("Creating fields table entry...");
            PreparedStatement createFieldsStatement = createFieldsInsert(conn, layerId, layerName, layerDescription);
            createFieldsStatement.execute();

        } catch (Exception ex) {
            ex.printStackTrace();
            conn.rollback();
            return false;
        }

        conn.commit();
        return true;
    }

    /**
     * Match a pattern with a single capturing group and return the content of
     * the capturing group
     * 
     * @param text
     *            the text to match against
     * @param pattern
     *            the pattern (regular expression) must contain one and only one
     *            capturing group
     * @return
     */
    private static String matchPattern(String text, String pattern) {
        // Use regular expression matching to pull the min and max values from
        // the output of gdalinfo
        Pattern p1 = Pattern.compile(pattern, Pattern.MULTILINE);
        Matcher m1 = p1.matcher(text);
        if (m1.find()) {
            if (m1.groupCount() == 1) {
                return m1.group(1);
            } else {
                throw new RuntimeException("error matching pattern " + pattern);
            }
        } else {
            throw new RuntimeException("error matching pattern " + pattern);
        }
    }

    private static PreparedStatement createLayersInsert(Connection conn, int layerId, String description, String path, String name, String displayPath, double minLatitude, double minLongitude,
            double maxLatitude, double maxLongitude, double valueMin, double valueMax, String units) throws SQLException {
        PreparedStatement stLayersInsert = conn
                .prepareStatement("INSERT INTO layers (id, name, description, type, path, displayPath, minlatitude, minlongitude, maxlatitude, maxlongitude, enabled, displayname, environmentalvaluemin, environmentalvaluemax, environmentalvalueunits, uid) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);");
        stLayersInsert.setInt(1, layerId);
        stLayersInsert.setString(2, name);
        stLayersInsert.setString(3, description);
        stLayersInsert.setString(4, "Environmental");
        stLayersInsert.setString(5, path);
        stLayersInsert.setString(6, displayPath);
        stLayersInsert.setDouble(7, minLatitude);
        stLayersInsert.setDouble(8, minLongitude);
        stLayersInsert.setDouble(9, maxLatitude);
        stLayersInsert.setDouble(10, maxLongitude);
        stLayersInsert.setBoolean(11, true);
        stLayersInsert.setString(12, description);
        stLayersInsert.setDouble(13, valueMin);
        stLayersInsert.setDouble(14, valueMax);
        stLayersInsert.setString(15, units);
        stLayersInsert.setInt(16, layerId);
        return stLayersInsert;
    }

    private static PreparedStatement createFieldsInsert(Connection conn, int layerId, String name, String description) throws SQLException {
        // TOOD slightly different statement if sdesc is null...

        PreparedStatement stFieldsInsert = conn
                .prepareStatement("INSERT INTO fields (name, id, \"desc\", type, spid, sid, sname, sdesc, indb, enabled, last_update, namesearch, defaultlayer, \"intersect\", layerbranch, analysis)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);");
        stFieldsInsert.setString(1, name);
        stFieldsInsert.setString(2, "el" + Integer.toString(layerId));
        stFieldsInsert.setString(3, description);
        stFieldsInsert.setString(4, "e");
        stFieldsInsert.setString(5, Integer.toString(layerId));
        stFieldsInsert.setNull(6, Types.VARCHAR);
        stFieldsInsert.setNull(7, Types.VARCHAR);
        stFieldsInsert.setNull(8, Types.VARCHAR);
        stFieldsInsert.setBoolean(9, true);
        stFieldsInsert.setBoolean(10, true);
        stFieldsInsert.setTimestamp(11, new Timestamp(System.currentTimeMillis()));
        stFieldsInsert.setBoolean(12, true);
        stFieldsInsert.setBoolean(13, false);
        stFieldsInsert.setBoolean(14, false);
        stFieldsInsert.setBoolean(15, false);
        stFieldsInsert.setBoolean(16, true);

        return stFieldsInsert;
    }

}
