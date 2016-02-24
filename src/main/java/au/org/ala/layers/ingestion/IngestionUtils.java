package au.org.ala.layers.ingestion;

import java.sql.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility functions to assist with layer ingestion.
 *
 * @author ChrisF
 */
public class IngestionUtils {

    public static final String CONTEXTUAL_LAYER_TYPE = "Contextual";
    public static final String ENVIRONMENTAL_LAYER_TYPE = "Environmental";
    public static final String CONTEXTUAL_FIELD_PREFIX = "cl";
    public static final String ENVIRONMENTAL_FIELD_PREFIX = "el";
    public static final String CONTEXTUAL_REGULAR_FIELD_TYPE = "c";
    public static final String CONTEXTUAL_FROM_GRID_CLASSES_FIELD_TYPE = "a";
    public static final String ENVIRONMENTAL_FIELD_TYPE = "e";
    public static final String GEOSERVER_QUERY_TEMPLATE = "<COMMON_GEOSERVER_URL>/gwc/service/wms?service=WMS&version=1.1.0&request=GetMap&layers=ALA:{0}&format=image/png&styles=";

    public static PreparedStatement createLayersInsertForEnvironmental(Connection conn, int layerId, String description, String path, String name, String displayPath, double minLatitude, double minLongitude,
                                                                       double maxLatitude, double maxLongitude, double valueMin, double valueMax, String units) throws SQLException {
        PreparedStatement stLayersInsert = conn
                .prepareStatement("INSERT INTO layers (id, name, description, type, path, displayPath, minlatitude, minlongitude, maxlatitude, maxlongitude, enabled, displayname, environmentalvaluemin, environmentalvaluemax, environmentalvalueunits, uid, path_orig) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);");
        stLayersInsert.setInt(1, layerId);
        stLayersInsert.setString(2, name);
        stLayersInsert.setString(3, description);
        stLayersInsert.setString(4, ENVIRONMENTAL_LAYER_TYPE);
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
        stLayersInsert.setString(16, Integer.toString(layerId));
        stLayersInsert.setString(17, "diva/" + name);
        return stLayersInsert;
    }

    public static PreparedStatement createLayersInsertForContextual(Connection conn, int layerId, String description, String path, String name, String displayPath, double minLatitude,
                                                                    double minLongitude, double maxLatitude, double maxLongitude, String path_orig) throws SQLException {
        PreparedStatement stLayersInsert = conn
                .prepareStatement("INSERT INTO layers (id, name, description, type, path, displayPath, minlatitude, minlongitude, maxlatitude, maxlongitude, enabled, displayname, uid, path_orig) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);");
        stLayersInsert.setInt(1, layerId);
        stLayersInsert.setString(2, name);
        stLayersInsert.setString(3, description);
        stLayersInsert.setString(4, CONTEXTUAL_LAYER_TYPE);
        stLayersInsert.setString(5, path);
        stLayersInsert.setString(6, displayPath);
        stLayersInsert.setDouble(7, minLatitude);
        stLayersInsert.setDouble(8, minLongitude);
        stLayersInsert.setDouble(9, maxLatitude);
        stLayersInsert.setDouble(10, maxLongitude);
        stLayersInsert.setBoolean(11, true);
        stLayersInsert.setString(12, description);
        stLayersInsert.setString(13, Integer.toString(layerId));
        stLayersInsert.setString(14, path_orig);
        return stLayersInsert;
    }

    public static PreparedStatement createObjectNameGenerationStatement(Connection conn) throws SQLException {
        PreparedStatement objectNameGenerationStatement = conn.prepareStatement(
                "INSERT INTO obj_names (name)" +
                " SELECT lower(objects.name) FROM fields " +
                " INNER JOIN objects ON objects.fid=fields.id" +
                " LEFT OUTER JOIN obj_names ON lower(objects.name) = obj_names.name" +
                " WHERE obj_names.name IS NULL" +
                " AND fields.namesearch = true" +
                " AND fields.id = objects.fid" +
                " GROUP BY lower(objects.name);" +
                " UPDATE objects SET name_id = obj_names.id FROM obj_names " +
                " WHERE name_id IS NULL AND lower(objects.name) = obj_names.name;"
        );

        return objectNameGenerationStatement;
    }

    public static PreparedStatement createGenerateObjectsBBoxAndAreaStatement(Connection conn) throws SQLException {
        PreparedStatement generateBBoxAndAreaStatement = conn.prepareStatement(
                "update objects set bbox = ST_AsText(Box2D(the_geom)) where bbox is null; "
                + "update objects set area_km=0 where st_geometrytype(the_geom) = 'ST_Point';");
        return generateBBoxAndAreaStatement;
    }

    public static PreparedStatement createFieldsInsert(Connection conn, int layerId, String name, String description,
                                                       String fieldId, String fieldType, String sid, String sname, String sdesc,
                                                       boolean indb, boolean enabled, boolean namesearch,
                                                       boolean defaultlayer, boolean intersect, boolean layerbranch,
                                                       boolean analysis, boolean addToMap) throws SQLException {
        PreparedStatement stFieldsInsert = conn
                .prepareStatement("INSERT INTO fields (name, id, \"desc\", type, spid, sid, sname, sdesc, indb, enabled, last_update, namesearch, defaultlayer, \"intersect\", layerbranch, analysis, addtomap)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);");
        stFieldsInsert.setString(1, name);
        stFieldsInsert.setString(2, fieldId);
        stFieldsInsert.setString(3, description);
        stFieldsInsert.setString(4, fieldType);
        stFieldsInsert.setString(5, Integer.toString(layerId));
        stFieldsInsert.setString(6, sid);
        stFieldsInsert.setString(7, sname);

        if (sdesc == null || sdesc.isEmpty()) {
            stFieldsInsert.setNull(8, Types.VARCHAR);
        } else {
            stFieldsInsert.setString(8, sdesc);
        }

        stFieldsInsert.setBoolean(9, indb);
        stFieldsInsert.setBoolean(10, enabled);
        stFieldsInsert.setTimestamp(11, new Timestamp(System.currentTimeMillis()));
        stFieldsInsert.setBoolean(12, namesearch);
        stFieldsInsert.setBoolean(13, defaultlayer);
        stFieldsInsert.setBoolean(14, intersect);
        stFieldsInsert.setBoolean(15, layerbranch);
        stFieldsInsert.setBoolean(16, analysis);
        stFieldsInsert.setBoolean(17, addToMap);

        return stFieldsInsert;
    }

    /**
     * Match a pattern with a single capturing group and return the content of
     * the capturing group
     *
     * @param text    the text to match against
     * @param pattern the pattern (regular expression) must contain one and only one
     *                capturing group
     * @return
     */
    public static String matchPattern(String text, String pattern) {
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
}
