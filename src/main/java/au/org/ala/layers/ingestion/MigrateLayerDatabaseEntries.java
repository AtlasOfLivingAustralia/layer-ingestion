package au.org.ala.layers.ingestion;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.Properties;

/**
 * Tool used to migrate layer data in database and geoserver from dev to prod.
 * NOTE: This tool assumes that the layer ready files (shape or geotiff etc),
 * layer analysis files and gwc_tiles have been already copied from dev to the
 * appropriate places on prod.
 * 
 * @author ChrisF
 * 
 */
public class MigrateLayerDatabaseEntries {
    
    public static final String LAYER_TYPE_COLUMN_NAME = "type";
    public static final String ENVIRONMENTAL_LAYER_TYPE = "Environmental";
    public static final String CONTEXTUAL_LAYER_TYPE = "Contextual";

    public static void main(String[] args) throws Exception {
        if (args.length < 7) {
            System.out.println("USAGE: layerId devDbJdbcUrl devDbUsername devDbPassword prodDbJdbcUrl prodDbUsername prodDbPassword");// prodGeoserverRestPath
        }

        int layerId = 0;
        try {
            layerId = Integer.parseInt(args[0]);
        } catch (NumberFormatException ex) {
            System.out.println("Invalid format for supplied layer id");
        }

        String devDbJdbcUrl = args[1];
        String devDbUsername = args[2];
        String devDbPassword = args[3];

        String prodDbJdbcUrl = args[4];
        String prodDbUsername = args[5];
        String prodDbPassword = args[6];

        /*
         * String prodGeoserverRestPath = args[6]; String prodGeoserverUsername
         * = args[7]; String prodGeoserverPassword = args[8];
         */

        /**
         * FOR ENVIRONMENTAL LAYER
         * 
         * copy layers table entry copy fields table entry create entries in
         * prod geoserver
         * 
         * FOR CONTEXTUAL LAYER
         * 
         * copy layers table entry copy fields table entry create shape file
         * table using shp2pgsql generate objects, object names etc (contextual
         * only) create entries in prod geoserver
         */

        Class.forName("org.postgresql.Driver");

        // Connect to dev database
        System.out.println("Connecting to dev database");
        Properties devProps = new Properties();
        devProps.setProperty("user", devDbUsername);
        devProps.setProperty("password", devDbPassword);
        Connection devConn = DriverManager.getConnection(devDbJdbcUrl, devProps);
        devConn.setAutoCommit(false);

        // Connect to prod database
        System.out.println("Connecting to prod database");
        Properties prodProps = new Properties();
        prodProps.setProperty("user", prodDbUsername);
        prodProps.setProperty("password", prodDbPassword);
        Connection prodConn = DriverManager.getConnection(prodDbJdbcUrl, prodProps);
        prodConn.setAutoCommit(false);

        try {
            // Read the layers table entry for the layer and write to prod
            PreparedStatement layersSelectStmt = devConn.prepareStatement("SELECT * from layers where id = ?;");
            layersSelectStmt.setInt(1, layerId);

            ResultSet devLayersResult = layersSelectStmt.executeQuery();

            boolean rowReturned = devLayersResult.next();
            if (!rowReturned) {
                throw new RuntimeException("No layers table entry for layer id " + layerId);
            }
            
            String layerType = devLayersResult.getString(LAYER_TYPE_COLUMN_NAME);
            
            PreparedStatement layersInsertStmt = createLayersInsertStatement(prodConn, devLayersResult);
            layersInsertStmt.execute();

            // Read the fields table entry (or entries) and write to prod
            // There may be more than one fields table entry in the case of a layer with mulitple classes,
            // such as the dynamic land cover layer.
            PreparedStatement fieldsSelectStmt = devConn.prepareStatement("Select * from fields where spid = ?;");
            fieldsSelectStmt.setString(1, Integer.toString(layerId));
            
            ResultSet devFieldsResult = fieldsSelectStmt.executeQuery();
            
            int numFieldsRows = 0;
            while(devFieldsResult.next()) {
                PreparedStatement fieldsInsertStatement = createFieldsInsertStatement(prodConn, devFieldsResult);
                fieldsInsertStatement.execute();
                numFieldsRows++;
            }
            
            // If layer type is contextual, and there is only 1 fields 
            
            
            prodConn.commit();
        } catch (Exception ex) {
            prodConn.rollback();
            ex.printStackTrace();
        }
    }

    private static PreparedStatement createLayersInsertStatement(Connection prodConn, ResultSet devLayersResult) throws Exception {
        PreparedStatement layersInsertStatement = prodConn.prepareStatement("INSERT INTO layers VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?);");
        insertPreparedStatementArgumentsFromResultSetCurrentRow(layersInsertStatement, devLayersResult);
        return layersInsertStatement;
    }
    
    private static PreparedStatement createFieldsInsertStatement(Connection prodConn, ResultSet devFieldsResult) throws Exception {
        PreparedStatement fieldsInsertStatement = prodConn.prepareStatement("INSERT INTO fields VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?);");
        insertPreparedStatementArgumentsFromResultSetCurrentRow(fieldsInsertStatement, devFieldsResult);
        return fieldsInsertStatement;
    }

    private static void insertPreparedStatementArgumentsFromResultSetCurrentRow(PreparedStatement stmt, ResultSet resultSet) throws Exception {
        ResultSetMetaData metadata = resultSet.getMetaData();

        for (int i = 1; i <= metadata.getColumnCount(); i++) {
            int columnType = metadata.getColumnType(i);
            stmt.setObject(i, resultSet.getObject(i), columnType);
        }
    }
}
