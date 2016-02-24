package au.org.ala.layers.ingestion;


import org.apache.commons.lang3.StringUtils;
import org.geotools.geometry.GeneralDirectPosition;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.CRS;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.geotools.referencing.operation.DefaultCoordinateOperationFactory;
import org.opengis.geometry.DirectPosition;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.CoordinateOperation;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * A importer for GML3 gazetteer information.
 * This was developed to consume OS Open Names export but might have utility for
 * other GML Gazetteers.
 */
public class GazetteerGML3Importer {

    public static void main(String[]  args) throws Exception {

        List<String> requiredArgs = new ArrayList<String>(Arrays.asList(new String[]{
                "filePathToGML",
                "layerId",
                "layerName",
                "layerDescription",
                "dbUserName",
                "dbPassword",
                "minLatitude",
                "maxLatitude",
                "minLongitude",
                "maxLongitude"
        }));

        if (args.length != requiredArgs.size()) {
            System.out.println("Usage: " + StringUtils.join(requiredArgs, " "));
            System.exit(1);
        }

        String filePath = args[requiredArgs.indexOf("filePathToGML")]; // "/Users/mar759/Downloads/OSOpenNamesETRS-89.gml";
        Integer layerId = Integer.parseInt(args[requiredArgs.indexOf("layerId")]);
        String layerName =  args[requiredArgs.indexOf("layerName")];      //"OS Open Names";
        String layerDescription = args[requiredArgs.indexOf("layerDescription")];

        double minLatitude = Double.parseDouble(args[requiredArgs.indexOf("minLatitude")]);
        double maxLatitude = Double.parseDouble(args[requiredArgs.indexOf("maxLatitude")]);
        double minLongitude = Double.parseDouble(args[requiredArgs.indexOf("minLongitude")]);
        double maxLongitude = Double.parseDouble(args[requiredArgs.indexOf("maxLongitude")]);

        String displayPath = filePath;
        boolean namesearch = true;
        boolean intersect = false;
        String fieldsSid = "name";
        String fieldsSname = "name";
        String fieldsSdesc = "name";

        System.out.println("Connecting to database");
        Class.forName("org.postgresql.Driver");
        Properties props = new Properties();
        props.setProperty("user", args[requiredArgs.indexOf("dbUserName")]);
        props.setProperty("password", args[requiredArgs.indexOf("dbPassword")]);
        Connection conn = DriverManager.getConnection("jdbc:postgresql://localhost/layersdb", props);
        conn.setAutoCommit(false);

        // insert to layers table
        System.out.println("Creating layers table entry...");
        PreparedStatement createLayersStatement = IngestionUtils.createLayersInsertForContextual(
                conn, layerId, layerDescription, displayPath, layerName,
                displayPath, minLatitude, minLongitude, maxLatitude, maxLongitude, "shape/" + layerName);
        createLayersStatement.execute();

        // insert to fields table
        System.out.println("Creating fields table entry...");
        String fieldId = IngestionUtils.CONTEXTUAL_FIELD_PREFIX + Integer.toString(layerId);
        PreparedStatement createFieldsStatement = IngestionUtils.createFieldsInsert(conn, layerId,
                layerDescription, layerDescription, fieldId, IngestionUtils.CONTEXTUAL_REGULAR_FIELD_TYPE,
                fieldsSid, fieldsSname, fieldsSdesc, true, true, namesearch, true, intersect, false, true, true);
        createFieldsStatement.execute();

        conn.commit();


        //OS Open Names
        File gmlFile = new File(filePath);
        try {

            FileInputStream fis = new FileInputStream(gmlFile);
            XMLInputFactory xmlInFact = XMLInputFactory.newInstance();
            XMLStreamReader reader = xmlInFact.createXMLStreamReader(fis);
            int featureCount = 0;

            ObjectToInsert feature = new ObjectToInsert();
            boolean isInSpellingName = false;
            boolean isInGeometry = false;
            boolean isInBoundedBy = false;
            String lowerCorner = null, upperCorner = null;

            while(reader.hasNext()) {

                int event = reader.next();

                if(event == XMLStreamReader.START_ELEMENT){
                    if("NamedPlace".equalsIgnoreCase(reader.getLocalName())) {
                        feature = new ObjectToInsert();
                        feature.id = reader.getAttributeValue(null, "id");
                    }
                    if("SpellingOfName".equalsIgnoreCase(reader.getLocalName())){
                        isInSpellingName = true;
                    }
                    if("geometry".equalsIgnoreCase(reader.getLocalName())){
                        isInGeometry = true;
                    }
                    if("Point".equalsIgnoreCase(reader.getLocalName()) && isInGeometry){
                        feature.pointCrs = reader.getAttributeValue(null, "srsName");
                    }
                    if("pos".equalsIgnoreCase(reader.getLocalName()) && isInGeometry){

                        String[] coordinates = reader.getElementText().split(" ");

                        //need to reproject to wgs84
                        CoordinateReferenceSystem wgs84CRS = DefaultGeographicCRS.WGS84;
                        CoordinateReferenceSystem sourceCRS = CRS.decode(feature.pointCrs);
                        CoordinateOperation transformOp = new DefaultCoordinateOperationFactory().createOperation(sourceCRS, wgs84CRS);
                        DirectPosition directPosition = new GeneralDirectPosition(Double.parseDouble(coordinates[0]), Double.parseDouble(coordinates[1]));
                        DirectPosition wgs84LatLong = transformOp.getMathTransform().transform(directPosition, null);

                        feature.longitude = wgs84LatLong.getOrdinate(0);
                        feature.latitude = wgs84LatLong.getOrdinate(1);
                    }
                    if("text".equalsIgnoreCase(reader.getLocalName()) && isInSpellingName){
                        feature.name = reader.getElementText();
                    }
                    if("boundedBy".equalsIgnoreCase(reader.getLocalName())){
                        isInBoundedBy = true;
                    }
                    if("Envelope".equalsIgnoreCase(reader.getLocalName()) && isInBoundedBy){
                        feature.bboxCrs = reader.getAttributeValue(null, "srsName");
                    }
                    if("upperCorner".equalsIgnoreCase(reader.getLocalName()) && isInBoundedBy){
                        upperCorner = reader.getElementText();;
                    }
                    if("lowerCorner".equalsIgnoreCase(reader.getLocalName()) && isInBoundedBy){
                        lowerCorner = reader.getElementText();;
                    }

                    if("inPopulatedPlace".equalsIgnoreCase(reader.getLocalName()) && isInBoundedBy){
                        feature.populatedPlace = reader.getAttributeValue(null, "title");
                    }
                    if("inCounty".equalsIgnoreCase(reader.getLocalName()) && isInBoundedBy){
                        feature.county = reader.getAttributeValue(null, "title");
                    }
                    if("inEuropeanRegion".equalsIgnoreCase(reader.getLocalName()) && isInBoundedBy){
                        feature.europeanRegion = reader.getAttributeValue(null, "title");
                    }
                    if("inCountry".equalsIgnoreCase(reader.getLocalName()) && isInBoundedBy){
                        feature.country = reader.getAttributeValue(null, "title");
                    }
                }

                if(event == XMLStreamReader.END_ELEMENT){
                    if("NamedPlace".equalsIgnoreCase(reader.getLocalName())){
                        featureCount++;
                        if(featureCount % 100 == 0){
                            System.out.println(featureCount + " :  " + feature.toString());
                        }

                        if(lowerCorner != null && upperCorner != null){

                            if(feature.bboxCrs !=null) {

                                CoordinateReferenceSystem crs = CRS.decode(feature.bboxCrs);
                                //create POLYGON BBOX - need to re-project.....
                                String[] lngLatLower = lowerCorner.split(" ");
                                String[] lngLatUpper = upperCorner.split(" ");

                                ReferencedEnvelope envelope = new ReferencedEnvelope(
                                        Double.parseDouble(lngLatLower[0]),
                                        Double.parseDouble(lngLatUpper[0]),
                                        Double.parseDouble(lngLatLower[1]),
                                        Double.parseDouble(lngLatUpper[1]),
                                        crs
                                );

                                ReferencedEnvelope envelopeWGS84 = envelope.transform(DefaultGeographicCRS.WGS84, true);
                                double[] lowerCornerPos = envelopeWGS84.getLowerCorner().getCoordinate();
                                double[] upperCornerPos = envelopeWGS84.getUpperCorner().getCoordinate();

                                lowerCornerPos[0] = Math.round(lowerCornerPos[0] * 100.0) / 100.0;
                                lowerCornerPos[1] = Math.round(lowerCornerPos[1] * 100.0) / 100.0;
                                upperCornerPos[0] = Math.round(upperCornerPos[0] * 100.0) / 100.0;
                                upperCornerPos[1] = Math.round(upperCornerPos[1] * 100.0) / 100.0;

                                String[] parts = new String[]{
                                        lowerCornerPos[0] + " " + lowerCornerPos[1],
                                        lowerCornerPos[0] + " " + upperCornerPos[1],
                                        upperCornerPos[0] + " " + upperCornerPos[1],
                                        upperCornerPos[0] + " " + lowerCornerPos[1],
                                        lowerCornerPos[0] + " " + lowerCornerPos[1]
                                };

                                String wkt = "POLYGON ((" + StringUtils.join(parts, ", ") + "))";
                                feature.bbox = wkt;
                            }
                        }

                        String sql = MessageFormat.format(
                                    "INSERT INTO objects " +
                                    " (pid, id, name, \"desc\", fid, the_geom, bbox, namesearch)" +
                                    " SELECT nextval(''objects_id_seq''::regclass), " +
                                    "''{0}'', " +
                                    "''{1}'', " +
                                    "''{2}'', " +
                                    "''{3}'', " +
                                    "ST_PointFromText(''POINT({4} {5})''), " +
                                    "''{6}'', " +
                                    "{7}",
                                feature.id,                                               //0
                                feature.name.replaceAll("'", "''"),                       //1
                                feature.getDescription(),                                 //2
                                fieldId,                                                  //3
                                feature.longitude,                                        //4
                                feature.latitude,                                         //5
                                feature.bbox,                                             //6
                                true                                                      //7
                        );

                        try {
                            //do a DB insert
                            PreparedStatement insertGaz = conn.prepareStatement(sql);
                            insertGaz.execute();

                            if (featureCount % 10000 == 0) {
                                conn.commit();
                            }
                        } catch (Exception e){
                            e.printStackTrace();
                        }
                    }
                    if("SpellingOfName".equalsIgnoreCase(reader.getLocalName())){
                        isInSpellingName = false;
                    }
                    if("geometry".equalsIgnoreCase(reader.getLocalName())){
                        isInGeometry = false;
                    }
                    if("lowerCorner".equalsIgnoreCase(reader.getLocalName()) && isInBoundedBy){
                        lowerCorner = reader.getElementText();
                    }
                    if("upperCorner".equalsIgnoreCase(reader.getLocalName()) && isInBoundedBy){
                        upperCorner = reader.getElementText();
                    }
                }
            }

            PreparedStatement objNamesStmt = IngestionUtils.createObjectNameGenerationStatement(conn);
            objNamesStmt.execute();

        } catch(IOException exc) {
            exc.printStackTrace();
        }
        catch(XMLStreamException exc) {
            exc.printStackTrace();
        }
    }

    public static class ObjectToInsert {

        public ObjectToInsert(){}

        public String id;
        public String name;
        public Double latitude;
        public Double longitude;
        public String pointCrs;
        public String bbox;
        public String bboxCrs;
        public String populatedPlace;
        public String county;
        public String europeanRegion;
        public String country;

        public String getDescription(){
            String description = "";
            if(populatedPlace != null){
                if(description.length() > 0) description += ", ";
                description += populatedPlace;
            }
            if(county != null){
                if(description.length() > 0) description += ", ";
                description += county;
            }
            if(europeanRegion != null){
                if(description.length() > 0) description += ", ";
                description += europeanRegion;
            }
            if(country != null){
                if(description.length() > 0) description += ", ";
                description += country;
            }
            return description.replaceAll("'", "''");
        }

        @Override
        public String toString() {
            return "" +
                   "name='" + name + '\'' +
                   ", geom='" + latitude  + ", " + longitude + '\'';
        }
    }
}
