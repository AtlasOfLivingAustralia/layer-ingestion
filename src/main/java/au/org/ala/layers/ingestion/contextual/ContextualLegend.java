package au.org.ala.layers.ingestion.contextual;

import au.org.ala.layers.legend.Legend;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.FileRequestEntity;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.*;
import java.net.URI;
import java.net.URL;
import java.util.*;

/**
 * Created by col52g on 15/06/15.
 */
public class ContextualLegend {

    // max objects that can be created in the sld
    static int MAX_OBJECTS = 500;

//    static String baseUrl = "http://spatial-dev.ala.org.au";

    static String layersBaseUrl;

    static String geoserverBaseUrl;

    public static void main(String[] args) {

        if(args.length != 6){
            System.out.println("Usage: layersBaseUrl geoserverBaseUrl validFields outputPath geoserverUsername geoserverPassword");
            System.exit(1);
        }

        layersBaseUrl = args[0];
        geoserverBaseUrl = args[1];
        List validFields = Arrays.asList(args[2].split(","));
        String outputPath = args[3];
        String geoserverUrl = geoserverBaseUrl;
        String geoserverUsername = args[4];
        String geoserverPassword = args[5];

        try {
            JSONParser jp = new JSONParser();

            //fetch all layers
            String url = layersBaseUrl + "/layers";
            InputStream stream = new URI(url).toURL().openStream();
            JSONArray layers = (JSONArray) jp.parse(IOUtils.toString(stream, "UTF-8"));
            stream.close();

            //fetch all fields
            url = layersBaseUrl + "/fields";
            stream = new URI(url).toURL().openStream();
            JSONArray fields = (JSONArray) jp.parse(IOUtils.toString(stream, "UTF-8"));
            stream.close();

            for (int i = 0; i < fields.size(); i++) {
                JSONObject field = (JSONObject) fields.get(i);

                if ("c".equals(field.get("type")) && validFields.contains(field.get("id"))) {

                    //find layer
                    JSONObject layer = null;
                    for (int j = 0; j < layers.size(); j++) {
                        if (String.valueOf(((JSONObject) layers.get(j)).get("id")).equals(field.get("spid"))) {
                            layer = (JSONObject) layers.get(j);
                        }
                    }

                    //make legend
                    if (layer != null) {
                        try {
                            String sld = null;

                            if (((String) field.get("sid")).contains(",")) {
                                sld = createContextualLayerSlds((String) field.get("sname"), (String) field.get("id"), false);
                            } else {
                                sld = createContextualLayerSlds((String) field.get("sid"), (String) field.get("id"), true);
                            }
                            if (sld == null) {
                                System.out.println("ERROR: sld is null");
                            } else {
                                File sldFile = new File(outputPath + '/' + layer.get("name") + ".sld");
                                FileUtils.write(sldFile, sld);

                                //upload sld
                                //Create style
                                String extra = "";
                                loadCreateStyle(geoserverUrl + "/rest/styles/",
                                        extra, geoserverUsername, geoserverPassword, field.get("id") + "_default_style");

                                //Upload sld
                                loadSld(geoserverUrl + "/rest/styles/" + field.get("id") + "_default_style",
                                        extra, geoserverUsername, geoserverPassword, sldFile.getPath());

                                //Apply style
                                String data = "<layer><enabled>true</enabled><defaultStyle><name>" + field.get("id") + "_default_style" +
                                        "</name></defaultStyle></layer>";
                                assignSld(geoserverUrl + "/rest/layers/ALA:" + layer.get("name"), extra,
                                        geoserverUsername, geoserverPassword, data);

                                String THUMBNAIL_WIDTH = "200";
                                String THUMBNAIL_HEIGHT = "200";

                                //get thumbnail
                                String thumb = geoserverUrl + "/wms/reflect?" +
                                        "layers=ALA:" + layer.get("name") +
                                        "&width=" + THUMBNAIL_WIDTH + "&height=" + THUMBNAIL_HEIGHT;

                                URL u = new URL(thumb);

                                InputStream inputStream = new BufferedInputStream(u.openStream());
                                ByteArrayOutputStream out = new ByteArrayOutputStream();
                                byte[] buf = new byte[1024];
                                int n;
                                while ((n = inputStream.read(buf)) > 0) {
                                    out.write(buf, 0, n);
                                }
                                out.close();
                                inputStream.close();

                                String filename = outputPath + layer.get("name") + ".jpg";
                                FileUtils.deleteQuietly(new File(filename));

                                FileOutputStream fos = new FileOutputStream(filename);
                                fos.write(out.toByteArray());
                                fos.close();

                                System.out.println("finished for sld: " + sldFile.getPath());
                            }
                        } catch (Exception err) {
                            err.printStackTrace();
                        }
                    } else {
                        System.out.println("failed");
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Create a coloured sld for a contextual layers.
     *
     * @param fieldId
     */
    static String createContextualLayerSlds(String colName, String fieldId, boolean useId) {
        String header = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<sld:UserStyle xmlns=\"http://www.opengis.net/sld\" xmlns:sld=\"http://www.opengis.net/sld\" xmlns:ogc=\"http://www.opengis.net/ogc\" xmlns:gml=\"http://www.opengis.net/gml\">\n" +
                "  <sld:Name>Default Styler</sld:Name>\n" +
                "  <sld:Title/>\n" +
                "  <sld:FeatureTypeStyle>\n" +
                "    <sld:Name>name</sld:Name>\n" +
                "    <sld:FeatureTypeName>Feature</sld:FeatureTypeName>";
        String footer = "</sld:FeatureTypeStyle>\n" +
                "</sld:UserStyle>";
        String rule = "<sld:Rule>\n" +
                "      <sld:Title>TITLE</sld:Title>\n" +
                "      <ogc:Filter>\n" +
                "        <ogc:PropertyIsEqualTo>\n" +
                "          <ogc:PropertyName>SNAME</ogc:PropertyName>\n" +
                "          <ogc:Literal>VALUE</ogc:Literal>\n" +
                "        </ogc:PropertyIsEqualTo>\n" +
                "      </ogc:Filter>\n" +
                "      <sld:PolygonSymbolizer>\n" +
                "        <sld:Fill>\n" +
                "          <sld:CssParameter name=\"fill\">#COLOUR</sld:CssParameter>\n" +
                "        </sld:Fill>\n" +
                "<sld:Stroke><sld:CssParameter name=\"stroke\">#000000</sld:CssParameter>" +
                "<sld:CssParameter name=\"stroke-width\">1</sld:CssParameter>" +
                "        </sld:Stroke>\n" +
                "      </sld:PolygonSymbolizer>\n" +
                "    </sld:Rule>";

        StringBuilder sld = new StringBuilder();
        sld.append(header);

        Set values = new HashSet();

        JSONArray objects = null;
        String url = layersBaseUrl + "/field/" + fieldId;
        try {
            JSONParser jp = new JSONParser();
            InputStream stream = new URI(url).toURL().openStream();
            objects = (JSONArray) ((JSONObject) jp.parse(IOUtils.toString(stream, "UTF-8"))).get("objects");
            stream.close();
        } catch (Exception err) {
            err.printStackTrace();
        }

        Map valueMap = new HashMap();

        for (int i = 0; i < objects.size(); i++) {
            JSONObject object = (JSONObject) objects.get(i);
            if (useId) {
                values.add(object.get("name"));
                Set list = (Set) valueMap.get(object.get("name"));
                if (list == null) list = new HashSet();
                list.add(object.get("id"));
                valueMap.put(object.get("name"), list);
            } else {
                values.add(object.get("name"));
                Set list = new HashSet();
                list.add(object.get("name"));
                valueMap.put(object.get("name"), list);
            }
        }

        List sortedValues = new ArrayList(values);

        //sort case insensitive
        Collections.sort(sortedValues, new Comparator() {
            @Override
            public int compare(Object o, Object t1) {
                return ((String) o).toLowerCase().compareTo(((String) t1).toLowerCase());
            }
        });

        if (values.size() == 0) {
            return null;
        } else {
            int count = 0;
            for (int i = 0; i < sortedValues.size(); i++) {
                String value = (String) sortedValues.get(i);

                double range = (double) values.size();
                double a = i / range;

                //10 colour steps
                int pos = (int) (a * 10);  //fit 0 to 10
                if (pos == 10) {
                    pos--;
                }
                double lower = (pos / 10.0) * range;
                double upper = ((pos + 1) / 10.0) * range;

                //translate value to 0-1 position between the colours
                double v = (i - lower) / (upper - lower);
                double vt = 1 - v;

                //there are groups+1 colours
                int red = (int) ((Legend.colours[pos] & 0x00FF0000) * vt + (Legend.colours[pos + 1] & 0x00FF0000) * v);
                int green = (int) ((Legend.colours[pos] & 0x0000FF00) * vt + (Legend.colours[pos + 1] & 0x0000FF00) * v);
                int blue = (int) ((Legend.colours[pos] & 0x00000FF) * vt + (Legend.colours[pos + 1] & 0x000000FF) * v);

                int ci = (red & 0x00FF0000) | (green & 0x0000FF00) | (blue & 0x000000FF) | 0xFF000000;

                String colour = String.format("%6s", Integer.toHexString(ci).substring(2).toUpperCase()).replace(" ", "0");
                if (value != null && valueMap.get(value) != null) {
                    count = count + 1;
                    Set set = (Set) valueMap.get(value);
                    for (Object o : set) {
                        String listValue = (String) o;

                        if (listValue != null) {
                            sld.append(rule.
                                    replace("TITLE", "" + value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")).
                                    replace("SNAME", "" + colName.replace("&", "&amp;").toLowerCase()).
                                    replace("COLOUR", "" + colour).
                                    replace("VALUE", "" + listValue.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")));
                        }
                    }
                }
            }

            if (count == 0) {
                return null;

            } else if (count > MAX_OBJECTS) {
                System.out.println("too many objects: " + count + " for field: " + fieldId);
                return null;
            } else {
                sld.append(footer);
            }
        }

        return sld.toString();
    }

    public static String loadSld(String url, String extra, String username, String password, String resourcepath) {
        System.out.println("loadSld url:" + url);
        System.out.println("path:" + resourcepath);

        String output = "";

        HttpClient client = new HttpClient();

        client.getState().setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username, password));
        client.getParams().setAuthenticationPreemptive(true);

        File input = new File(resourcepath);

        PutMethod put = new PutMethod(url);
        put.setDoAuthentication(true);

        // Request content will be retrieved directly
        // from the input stream
        RequestEntity entity = new FileRequestEntity(input, "application/vnd.ogc.sld+xml");
        put.setRequestEntity(entity);

        // Execute the request
        try {
            int result = client.executeMethod(put);

            // get the status code
            System.out.println("Response status code: " + result);

            // Display response
            System.out.println("Response body: ");
            System.out.println(put.getResponseBodyAsString());

            output += result;

        } catch (Exception e) {
            System.out.println("Something went wrong with UploadSpatialResource");
            e.printStackTrace(System.out);
        } finally {
            // Release current connection to the connection pool once you are done
            put.releaseConnection();
        }

        return output;
    }

    public static String loadCreateStyle(String url, String extra, String username, String password, String name) {
        System.out.println("loadCreateStyle url:" + url);
        System.out.println("name:" + name);

        String output = "";

        HttpClient client = new HttpClient();

        client.getState().setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username, password));

        PostMethod post = new PostMethod(url);
        post.setDoAuthentication(true);

        // Execute the request
        try {
            // Request content will be retrieved directly
            // from the input stream
            File file = File.createTempFile("sld", "xml");
            System.out.println("file:" + file.getPath());
            FileWriter fw = new FileWriter(file);
            fw.append("<style><name>" + name + "</name><filename>" + name + ".sld</filename></style>");
            fw.close();
            RequestEntity entity = new FileRequestEntity(file, "text/xml");
            post.setRequestEntity(entity);

            int result = client.executeMethod(post);

            // get the status code
            System.out.println("Response status code: " + result);

            // Display response
            System.out.println("Response body: ");
            System.out.println(post.getResponseBodyAsString());

            output += result;

        } catch (Exception e) {
            System.out.println("Something went wrong with UploadSpatialResource");
            e.printStackTrace(System.out);
        } finally {
            // Release current connection to the connection pool once you are done
            post.releaseConnection();
        }

        return output;
    }

    public static String assignSld(String url, String extra, String username, String password, String data) {
        System.out.println("assignSld url:" + url);
        System.out.println("data:" + data);

        String output = "";

        HttpClient client = new HttpClient();

        client.getState().setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username, password));

        PutMethod put = new PutMethod(url);
        put.setDoAuthentication(true);

        // Execute the request
        try {
            // Request content will be retrieved directly
            // from the input stream
            File file = File.createTempFile("sld", "xml");
            System.out.println("file:" + file.getPath());
            FileWriter fw = new FileWriter(file);
            fw.append(data);
            fw.close();
            RequestEntity entity = new FileRequestEntity(file, "text/xml");
            put.setRequestEntity(entity);

            int result = client.executeMethod(put);

            // get the status code
            System.out.println("Response status code: " + result);

            // Display response
            System.out.println("Response body: ");
            System.out.println(put.getResponseBodyAsString());

            output += result;

        } catch (Exception e) {
            System.out.println("Something went wrong with UploadSpatialResource");
            e.printStackTrace(System.out);
        } finally {
            // Release current connection to the connection pool once you are done
            put.releaseConnection();
        }

        return output;
    }
}
