package au.org.ala.layers.ingestion;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URI;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.Properties;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

public class ExpertDistributionsDataImport {

    public static void main(String[] args) throws Exception {
        Class.forName("org.postgresql.Driver");
        String url = "jdbc:postgresql://ala-devmaps-db.vm.csiro.au:5432/layersdb";
        Properties props = new Properties();
        props.setProperty("user", "postgres");
        props.setProperty("password", "postgres");
        Connection conn = DriverManager.getConnection(url, props);
        conn.setAutoCommit(false);

        ((org.postgresql.PGConnection) conn).addDataType("geometry", org.postgis.PGgeometry.class);
        ((org.postgresql.PGConnection) conn).addDataType("box3d", org.postgis.PGbox3d.class);

        if(args.length==1){
            load(args[0], conn);
        } else if(args.length>1){
            String file = args[0];
            //rematch and write to the file
            if(args[1].equals("-rematch")){
                rematch(file,conn);
            }   else if(args[1].equals("-load")){
                load(file, conn);
            }
        }
    }

    /**
     * A rematch option to allow for a rematch of the species to occur without reloading the distribution tables.
     *
     * It will produce a SQL script that can be used to update on either server.
     *
     * @param filename The absolute filename for the SQL file to produce
     * @param conn
     * @throws Exception
     */
    private static void rematch(String filename, Connection conn) throws Exception{
        String select="SELECT spcode, scientific,family,genus_name,lsid,family_lsid,genus_lsid from distributions";
        FileOutputStream fos=FileUtils.openOutputStream(new File(filename));
        Statement st = conn.createStatement();
        ResultSet rs = st.executeQuery(select);
        String updateStatement ="UPDATE distributions set lsid=%s, family_lsid=%s, genus_lsid=%s where spcode=%s;\n";
        int count =0,rematched=0;
        try{
        while (rs.next()){
            String spcode = rs.getString(1);
            String scientificName =rs.getString(2);
            String family = rs.getString(3);
            String genusName = rs.getString(4);
            String currentLsid = rs.getString(5);
            String currentFamilyLsid= rs.getString(6);
            String currentGenusLsid=rs.getString(7);
            String lsid = lookupSpeciesOrFamilyLsid(scientificName);
            String familyLsid = family != null && family.length()>0? lookupSpeciesOrFamilyLsid(family):null;
            String genusLsid = genusName != null && genusName.length()>0? lookupGenusLsid(genusName):null;
            count++;
            if(!StringUtils.equals(lsid, currentLsid)){

                if(lsid == null){
                    System.out.println("Issue with " +spcode +" for scientific name " + scientificName);
                } else {
                    //System.out.println("LSIDs are different: " + lsid + " " + currentLsid +" sci : " + scientificName);
                    //String.format("SELECT * from distributiondata where spcode = %s", spCode)
                    String line = String.format(updateStatement, toParamString(lsid), toParamString(familyLsid), toParamString(genusLsid),spcode);
                    fos.write(line.getBytes());

                    rematched++;
                }
            }
        }
        } catch(Exception e){
            throw e;
        } finally{
            fos.flush();
            fos.close();
            rs.close();
        }
        System.out.println("Rematched " + rematched + " out of " + count);
    }
    private static String toParamString(String value){
        return value == null?"null":"'"+value+"'";
    }

    private static void load(String filename, Connection conn) throws Exception{


        String fileName = filename;

        String fileContent = FileUtils.readFileToString(new File(fileName));

        String[] fileLines = fileContent.split("\\n");

        int rowsAdded = 0;
        int rowsUpdated = 0;
        for (String line : fileLines) {
            String[] lineParts = line.split(";");

            String spCode = lineParts[0];
            String scientificName = lineParts[1].replace("\"", "");
            String authorityFull = lineParts[2].replace("\"", "");
            String commonName = lineParts[3].replace("\"", "");
            String family = lineParts[4].replace("\"", "");
            String genusName = lineParts[5].replace("\"", "");
            String specificName = lineParts[6].replace("\"", "");
            String minDepth = lineParts[7].replace("\"", "");
            String maxDepth = lineParts[8].replace("\"", "");
            String pelagicFlag = lineParts[9].replace("\"", "");
            String estuarineFlag = lineParts[10].replace("\"", "");
            String coastalFlag = lineParts[11].replace("\"", "");
            String desmersalFlag = lineParts[12].replace("\"", "");
            String groupName = lineParts[13].replace("\"", "");
            String genusExemplar = lineParts[14].replace("\"", "");
            String familyExemplar = lineParts[15].replace("\"", "");
            String caabSpeciesNumber = lineParts[16].replace("\"", "");
            String caabSpeciesURL = lineParts[17].replace("\"", "");
            String caabFamilyNumber = lineParts[18].replace("\"", "");
            String caabFamilyURL = lineParts[19].replace("\"", "");
            String metadataUUID = lineParts[20].replace("\"", "");
            String metadataURL = lineParts[21].replace("\"", "");

            String lsid = lookupSpeciesOrFamilyLsid(scientificName);
            String familyLsid = lookupSpeciesOrFamilyLsid(family);
            String genusLsid = lookupGenusLsid(genusName);

            // check if species code is already in database
            // Get ID to use for layer
            try {
                Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery(String.format("SELECT * from distributiondata where spcode = %s", spCode));
                if (rs.next()) {
                    // update existing row with new values
                    PreparedStatement stUpdate = conn.prepareStatement("UPDATE distributiondata SET scientific = ?, authority_ = ?, common_nam = ?, family = ?,"
                            + " genus_name = ?, specific_n = ?, min_depth = ?, max_depth = ?, pelagic_fl = ?, " + " lsid = ?, type = ?, estuarine_fl = ?, coastal_fl = ?, desmersal_fl = ?,"
                            + " group_name = ?, genus_exemplar = ?, family_exemplar = ?, caab_species_number = ?, caab_species_url = ?, caab_family_number = ?,"
                            + " caab_family_url = ?, metadata_uuid = ?, metadata_u = ?, family_lsid = ?, genus_lsid = ? WHERE spcode = ?;");
                    stUpdate.setString(1, scientificName);
                    stUpdate.setString(2, authorityFull);
                    stUpdate.setString(3, commonName);
                    stUpdate.setString(4, family);
                    stUpdate.setString(5, genusName);
                    stUpdate.setString(6, specificName);

                    setAsNullOrInteger(minDepth, 7, stUpdate);
                    setAsNullOrInteger(maxDepth, 8, stUpdate);
                    setAsNullOrInteger(pelagicFlag, 9, stUpdate);

                    stUpdate.setString(10, lsid);
                    stUpdate.setString(11, "e");

                    setAsNullOrInteger(estuarineFlag, 12, stUpdate);
                    setAsNullOrInteger(coastalFlag, 13, stUpdate);
                    setAsNullOrInteger(desmersalFlag, 14, stUpdate);

                    stUpdate.setString(15, groupName);
                    stUpdate.setBoolean(16, genusExemplar.equals("1"));
                    stUpdate.setBoolean(17, familyExemplar.equals("1"));
                    stUpdate.setString(18, caabSpeciesNumber);
                    stUpdate.setString(19, caabSpeciesURL);
                    stUpdate.setString(20, caabFamilyNumber);
                    stUpdate.setString(21, caabFamilyURL);
                    stUpdate.setString(22, metadataUUID);
                    stUpdate.setString(23, metadataURL);
                    stUpdate.setString(24, familyLsid);
                    stUpdate.setString(25, genusLsid);
                    stUpdate.setInt(26, Integer.parseInt(spCode));

                    int result = stUpdate.executeUpdate();

                    rowsUpdated++;
                    System.out.println("Updated " + scientificName + " (" +  spCode + ")");
                } else {
                    // insert new row
                    PreparedStatement stInsert = conn.prepareStatement(String
                            .format("INSERT INTO distributiondata VALUES (DEFAULT, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);"));
                    stInsert.setInt(1, Integer.parseInt(spCode));
                    stInsert.setString(2, scientificName);
                    stInsert.setString(3, authorityFull);
                    stInsert.setString(4, commonName);
                    stInsert.setString(5, family);
                    stInsert.setString(6, genusName);
                    stInsert.setString(7, specificName);

                    setAsNullOrInteger(minDepth, 8, stInsert);
                    setAsNullOrInteger(maxDepth, 9, stInsert);
                    setAsNullOrInteger(pelagicFlag, 10, stInsert);

                    stInsert.setString(11, metadataURL);

                    stInsert.setObject(12, null);
                    stInsert.setString(13, null);
                    stInsert.setString(14, lsid);
                    stInsert.setNull(15, Types.INTEGER);
                    stInsert.setString(16, "e");
                    stInsert.setString(17, null);
                    stInsert.setString(18, null);

                    setAsNullOrInteger(estuarineFlag, 19, stInsert);
                    setAsNullOrInteger(coastalFlag, 20, stInsert);
                    setAsNullOrInteger(desmersalFlag, 21, stInsert);

                    stInsert.setString(22, groupName);
                    stInsert.setBoolean(23, genusExemplar.equals("1"));
                    stInsert.setBoolean(24, familyExemplar.equals("1"));
                    stInsert.setString(25, caabSpeciesNumber);
                    stInsert.setString(26, caabSpeciesURL);
                    stInsert.setString(27, caabFamilyNumber);
                    stInsert.setString(28, caabFamilyURL);
                    stInsert.setString(29, metadataUUID);
                    stInsert.setString(30, familyLsid);
                    stInsert.setString(31, genusLsid);

                    int result = stInsert.executeUpdate();

                    rowsAdded++;
                    System.out.println("Added " + scientificName + " (" + spCode + ")");
                }
                rs.close();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        try {
            conn.commit();
            System.out.println(rowsUpdated + " rows updated");
            System.out.println(rowsAdded + " rows added");
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private static void setAsNullOrInteger(String inputString, int index, PreparedStatement stmt) throws SQLException {
        if (inputString.isEmpty()) {
            stmt.setNull(index, Types.INTEGER);
        } else {
            stmt.setInt(index, Integer.parseInt(inputString));
        }
    }

    private static String lookupSpeciesOrFamilyLsid(String id) throws Exception {
        HttpClient client = new DefaultHttpClient();

        URL wsUrl = new URL("http://bie.ala.org.au/ws/guid/" + id);
        URI uri = new URI(wsUrl.getProtocol(), wsUrl.getAuthority(), wsUrl.getPath(), wsUrl.getQuery(), wsUrl.getRef());

        HttpGet get = new HttpGet(uri.toURL().toString());
        HttpResponse response = client.execute(get);
        if (response.getStatusLine().getStatusCode() != 200) {
            throw new IllegalStateException("Fetching of species or family LSID failed " +  response.getStatusLine().getReasonPhrase() + " " + id);
        }

        HttpEntity entity = response.getEntity();
        String responseContent = IOUtils.toString(entity.getContent());

        JSONArray jsonArr = (JSONArray) new JSONParser().parse(responseContent);
        String lsid = null;
        if (jsonArr.size() > 0) {
            JSONObject jsonObj = (JSONObject) jsonArr.get(0);

            lsid = (String) jsonObj.get("acceptedIdentifier");
        }
        
        return lsid;
    }

    // Need to use a different web service to lookup genus lsids
    private static String lookupGenusLsid(String id) throws Exception {
        HttpClient client = new DefaultHttpClient();

        URL wsUrl = new URL("http://bie.ala.org.au/search.json?fq=rank:genus&q=" + id);
        URI uri = new URI(wsUrl.getProtocol(), wsUrl.getAuthority(), wsUrl.getPath(), wsUrl.getQuery(), wsUrl.getRef());

        HttpGet get = new HttpGet(uri.toURL().toString());
        HttpResponse response = client.execute(get);
        if (response.getStatusLine().getStatusCode() != 200) {
            throw new IllegalStateException("Fetching of species or family LSID failed");
        }

        HttpEntity entity = response.getEntity();
        String responseContent = IOUtils.toString(entity.getContent());

        JSONObject jsonObj = (JSONObject) new JSONParser().parse(responseContent);

        String genusLsid = null;

        if (jsonObj.containsKey("searchResults")) {
            JSONObject searchResultsObj = (JSONObject) jsonObj.get("searchResults");
            if (searchResultsObj.containsKey("results")) {
                JSONArray resultsArray = (JSONArray) searchResultsObj.get("results");
                if (resultsArray.size() > 0) {
                    JSONObject firstResult = (JSONObject) resultsArray.get(0);
                    if (firstResult.containsKey("guid")) {
                        genusLsid = (String) firstResult.get("guid");
                    }
                }
            }
        }

        return genusLsid;
    }
}
