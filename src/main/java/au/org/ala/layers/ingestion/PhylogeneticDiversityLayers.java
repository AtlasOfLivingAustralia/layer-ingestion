package au.org.ala.layers.ingestion;

import au.com.bytecode.opencsv.CSVReader;
import au.org.ala.spatial.analysis.layers.Records;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.io.FileUtils;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Created by a on 28/07/2014.
 */
public class PhylogeneticDiversityLayers {
    static double gridSize = 0.5;
    static String currentPath = "/data/";

    public static void main(String[] args) {
        System.out.println("usage: " +
                "PhylogeneticDiversityLayers minx maxx miny maxy step phylolist_url");

        args = new String[]{"112", "154", "-44", "-12", "0.5", "http://115.146.93.110/PhyloLink"};
        double minx = Double.parseDouble(args[0]);
        double maxx = Double.parseDouble(args[1]);
        double miny = Double.parseDouble(args[2]);
        double maxy = Double.parseDouble(args[3]);
        double step = Double.parseDouble(args[4]);
        gridSize = step;
        String phylolist_url = args[5];

        //get list of trees
        JSONArray trees = null;
        try {
            String url = phylolist_url + "/phylo/getExpertTrees";
            HttpClient client = new HttpClient();
            GetMethod get = new GetMethod(url);

            client.executeMethod(get);

            JSONObject j2 = JSONObject.fromObject(streamToString(get.getResponseBodyAsStream()));
            trees = j2.getJSONArray("expertTrees");
        } catch (Exception e) {
            e.printStackTrace();
        }

        //get species lists from trees
        ArrayList<List> speciesLists = new ArrayList<List>();
        for (int i = 0; i < trees.size(); i++) {
            JSONObject jo = trees.getJSONObject(i);

            String speciesTree = jo.getString("tree");

            //currently speciesTree can be seen as ' delimited list with species names
            List<String> result = new ArrayList<String>();
            for (String s : speciesTree.split("'")) {
                if (s.charAt(0) >= 'A' && s.charAt(0) <= 'Z') {
                    result.add(s);
                }
            }
            speciesLists.add(result);
        }

        //create biocache qid for species lists
        List<List> qids = new ArrayList();
        for (int j = 0; j < speciesLists.size(); j++) {
            List list = speciesLists.get(j);
            List group = new ArrayList();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < list.size(); i++) {
                if (i > 0 && i % 200 == 0) {
                    //split
                    group.add(sb.toString());
                    sb = new StringBuilder();
                }
                if (sb.length() > 0) {
                    sb.append(" OR ");
                }
                sb.append("taxon_name:\"").append(list.get(i)).append("\"");
            }
            group.add(sb.toString());

            for (int i = 0; i < group.size(); i++) {
                HttpClient client = new HttpClient();
                String url = null;
                String response = "";
                try {
                    url = "http://biocache.ala.org.au/ws/webportal/params?"

                            + "&facet=false";

                    PostMethod post = new PostMethod(url);

                    post.addParameter("q", (String) group.get(i));

                    System.out.println((String) group.get(i));


                    int result = 0;


                    result = client.executeMethod(post);

                    response = post.getResponseBodyAsString();

                    group.set(i, response);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            qids.add(group);

            //build
            speciesRichnessLayer(group, trees.getJSONObject(j).getString("treeId"));
        }

        String str = "";

        try {
            HashMap<String, FileWriter> files = new HashMap<String, FileWriter>();

            for (double y = maxy; y >= miny; y -= step) {
                for (double x = minx; x <= maxx; x += step) {
                    System.out.println(x + " " + y + ", ");

                    //get species list
                    String q = "q=longitude:%5B" + x + "%20TO%20" + (double) (x + (step - 1) + 0.9999999999999) + "%5D&fq=latitude:%5B" + y + "%20TO%20" + (double) (y + (step - 1) + 0.99999999999999) + "%5D";
                    String url = "http://biocache.ala.org.au/ws/occurrences/facets/download?facets=taxon_name&flimit=1000000&" + q;

                    HttpClient client = new HttpClient();
                    GetMethod get = new GetMethod(url);

                    client.executeMethod(get);

                    str = streamToString(get.getResponseBodyAsStream());
                    System.out.println(url);
                    //str = get.getResponseBodyAsString();

                    CSVReader r = new CSVReader(new StringReader(str));
                    JSONArray list = new JSONArray();
                    for (String[] s : r.readAll()) {
                        list.add(s[0]);
                    }

                    System.out.println("species count: " + list.size());

                    //get pd
                    HashMap<String, String> pds = new HashMap<String, String>();
                    for (int i = 0; i < trees.size(); i++) {
                        pds.put(trees.getJSONObject(i).getString("treeId"), "0");
                    }
                    if (list.size() > 0) {
                        try {
                            url = phylolist_url + "/phylo/getPD";
                            PostMethod post = new PostMethod(url);
                            NameValuePair[] nvp = new NameValuePair[2];
                            nvp[0] = new NameValuePair("noTreeText", "true");
                            nvp[1] = new NameValuePair("speciesList", list.toString());

                            post.setRequestBody(nvp);

                            client.executeMethod(post);

                            str = streamToString(post.getResponseBodyAsStream());
                            JSONArray j4 = JSONArray.fromObject(str);

                            for (int i = 0; i < j4.size(); i++) {
                                JSONObject j5 = j4.getJSONObject(i);
                                String treeId = j5.getString("treeId");
                                String pd = j5.getString("pd");
                                System.out.println("pd: " + pd);
                                pds.put(treeId, pd);
                            }
                        } catch (Exception e) {
                            e.printStackTrace(System.out);

                            //write out failed list
                            System.out.println(list.toString());

                            //write out response
                            System.out.println("response:" + str);
                        }
                    }

                    //write to files
                    for (String treeId : pds.keySet()) {
                        String pd = pds.get(treeId);
                        FileWriter fw = null;

                        if (files.containsKey(treeId)) {
                            fw = files.get(treeId);
                        } else {
                            fw = new FileWriter(new File("/data/" + treeId + ".asc"));

                            //header
                            fw.write("NCOLS " + (int) ((maxx - minx) / step + 1) + "\n");
                            fw.write("NROWS " + (int) ((maxy - miny) / step + 1) + "\n");
                            fw.write("XLLCORNER " + minx + "\n");
                            fw.write("YLLCORNER " + miny + "\n");
                            fw.write("CELLSIZE " + step + "\n");
                            fw.write("NODATA_VALUE -9999\n");

                            //store for reuse
                            files.put(treeId, fw);
                        }

                        //write pd value to map.asc
                        if (x > minx) {
                            fw.write(" ");
                        }
                        fw.write(pd);
                    }
                }

                if (y - step >= miny) {
                    for (FileWriter fw : files.values()) {
                        fw.write("\n");
                        fw.flush();
                    }
                }
            }

            for (FileWriter fw : files.values()) {
                fw.flush();
                fw.close();
            }
        } catch (Exception e) {
            e.printStackTrace(System.out);
        }
    }

    private static void speciesRichnessLayer(List qidGroup, String treeName) {
        double[] bbox = {112, -44, 154, -12};
        try {
            //fetch and merge records
            File joinedRecords = new File("/data/joinedRecords");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < qidGroup.size(); i++) {
                String file = "/data/records" + i;
                Records r = new Records("http://biocache.ala.org.au/ws",
                        "qid:" + qidGroup.get(i), bbox, file, null);

                sb.append(FileUtils.readFileToString(new File(file)));
                sb.append("\n");
            }
            FileUtils.write(joinedRecords, sb.toString());

            Records records = new Records(joinedRecords.getPath());

//            SpeciesDensity sd = new SpeciesDensity(1, gridSize, bbox);
            //          sd.write(records, currentPath + File.separator, "species_richness_" + treeName + ".asc", AlaspatialProperties.getAnalysisThreadCount(), true, true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String streamToString(InputStream is) {
        StringWriter sw = new StringWriter();

        try {
            InputStreamReader in2 = new InputStreamReader(is, "UTF-8");
            int c;
            while ((c = in2.read()) != -1) {
                sw.write(c);
            }
            in2.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return sw.toString();
    }
}
