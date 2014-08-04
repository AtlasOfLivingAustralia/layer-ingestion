package au.org.ala.layers.ingestion;

import au.com.bytecode.opencsv.CSVReader;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;

import java.io.*;
import java.util.HashMap;

/**
 * Created by a on 28/07/2014.
 */
public class PhylogeneticDiversityLayers {
    public static void main(String [] args) {
        System.out.println("usage: " +
                "PhylogeneticDiversityLayers minx maxx miny maxy step phylolist_url");

        args = new String[] {"112","154","-44","-12","1","http://115.146.93.110/PhyloLink"};
        double minx = Double.parseDouble(args[0]);
        double maxx = Double.parseDouble(args[1]);
        double miny = Double.parseDouble(args[2]);
        double maxy = Double.parseDouble(args[3]);
        double step = Double.parseDouble(args[4]);
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

        String str = "";

        try {
            HashMap<String, FileWriter> files = new HashMap<String, FileWriter>();

            for(double y = maxy; y>= miny; y-= step) {
                for (double x = minx; x <= maxx; x += step) {
                    System.out.println(x + " " + y + ", ");

                    //get species list
                    String q = "q=longitude:%5B" + x + "%20TO%20" + (double)(x+(step-1)+0.99999999999999) + "%5D&fq=latitude:%5B" + y + "%20TO%20" + (double)(y+(step-1)+0.99999999999999) + "%5D";
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
                    for(int i=0;i<trees.size();i++) {
                        pds.put(trees.getJSONObject(i).getString("treeId"),"0");
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
                    for(String treeId : pds.keySet()) {
                        String pd = pds.get(treeId);
                        FileWriter fw = null;

                        if (files.containsKey(treeId)) {
                            fw = files.get(treeId);
                        } else {
                            fw = new FileWriter(new File("/data/" + treeId + ".asc"));

                            //header
                            fw.write("NCOLS " + (int)((maxx - minx) / step + 1) + "\n");
                            fw.write("NROWS " + (int)((maxy - miny) / step + 1) + "\n");
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
