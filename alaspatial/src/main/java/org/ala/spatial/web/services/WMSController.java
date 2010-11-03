package org.ala.spatial.web.services;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.ala.spatial.analysis.heatmap.HeatMap;
import org.ala.spatial.analysis.index.IndexedRecord;
import org.ala.spatial.analysis.index.OccurrencesIndex;
import org.ala.spatial.dao.SpeciesDAO;
import org.ala.spatial.model.Species;
import org.ala.spatial.util.TabulationSettings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Provides an interface to the BIE (or other apps) to display either density
 * or point based images for a given filter:
 *  - family lsid
 *  - genus lsid
 *  - species lsid
 *  - subspecies lsid
 *  - institution code
 *  - institution code & collection code
 *  - data provider id
 *  - dataset id
 *
 * eg: 
 *  http://localhost:8080/alaspatial/ws/density/map?species_lsid=urn:lsid:biodiversity.org.au:apni.taxon:295866
 *  http://localhost:8080/alaspatial/ws/density/map?institution_code=WAM&collection_code=MAMM&collection_code=ARACH
 *
 * 
 * @author ajay
 */
@Controller
@RequestMapping("/ws/density")
public class WMSController {

    String baseOutputPath = "";
    String baseOutUrl = "/output/sampling/";
    private SpeciesDAO speciesDao;

    @Autowired
    public void setSpeciesDao(SpeciesDAO speciesDao) {
        this.speciesDao = speciesDao;
    }

    @RequestMapping(value = "/map", method = RequestMethod.GET)
    public
    @ResponseBody
    Map getDensityMap(
            @RequestParam(value = "spname", required = false, defaultValue = "") String spname,
            @RequestParam(value = "tcid", required = false, defaultValue = "") String tcid,
            @RequestParam(value = "family_lsid", required = false, defaultValue = "") String family_lsid,
            @RequestParam(value = "genus_lsid", required = false, defaultValue = "") String genus_lsid,
            @RequestParam(value = "species_lsid", required = false, defaultValue = "") String species_lsid,
            @RequestParam(value = "subspecies_lsid", required = false, defaultValue = "") String subspecies_lsid,
            @RequestParam(value = "institutionUid", required = false, defaultValue = "") String institutionUid,
            @RequestParam(value = "collectionUid", required = false, defaultValue = "") String collectionUid,
            @RequestParam(value = "dataProviderUid", required = false, defaultValue = "") String dataProviderUid,
            @RequestParam(value = "dataResourceUid", required = false, defaultValue = "") String dataResourceUid,
            HttpServletRequest request, HttpServletResponse response) {

        String msg = "";
        Map output = new HashMap();
        boolean isHeatmap = true;

        HttpSession session = request.getSession();

        TabulationSettings.load();

        baseOutUrl = TabulationSettings.base_output_url + "/output/sampling/";

        try {
            String lsid = "";
            if (family_lsid != null) {
                System.out.print("generating family density map for: " + family_lsid);
                //msg = "generating density family map for: " + family_lsid;
                lsid = family_lsid;
            } else if (genus_lsid != null) {
                System.out.print("generating genus density map for: " + genus_lsid);
                //msg = "generating density genus map for: " + genus_lsid;
                lsid = genus_lsid;
            } else if (species_lsid != null) {
                System.out.print("generating species density map for: " + species_lsid);
                //msg = "generating density species map for: " + species_lsid;
                lsid = species_lsid;
            } else if (subspecies_lsid != null) {
                System.out.print("generating subspecies density map for: " + subspecies_lsid);
                //msg = "generating density subspecies map for: " + subspecies_lsid;
                lsid = subspecies_lsid;
            } else if (tcid != null) {
                System.out.print("generating taxonconceptid density map for: " + tcid);
                //msg = "generating density subspecies map for: " + subspecies_lsid;
                //lsid = tcid;
            }

            //String currentPath = session.getServletContext().getRealPath(File.separator);
            String currentPath = TabulationSettings.base_output_dir;
            baseOutputPath = TabulationSettings.base_output_dir;
            File baseDir = new File(currentPath + "output" + File.separator + "sampling" + File.separator);
            if (!lsid.equalsIgnoreCase("")) {
                String outputfile = baseDir + File.separator + lsid.replace(":", "_") + ".png";
                System.out.println("Checking if already present: " + outputfile);
                File imgFile = new File(outputfile);
                if (imgFile.exists()) {
                    System.out.println("File already present, sending that: " + baseOutUrl + lsid.replace(":", "_") + ".png");
                    //msg = baseOutUrl + lsid.replace(":", "_") + ".png";

                    // output = generateOutput(baseOutUrl + lsid.replace(":", "_") + ".png", baseOutUrl + "legend_" + lsid.replace(":", "_") + ".png", isHeatmap);
                    output = generateOutput(lsid.replace(":", "_") + ".png", "legend_" + lsid.replace(":", "_") + ".png", "");

                } else {
                    System.out.println("Starting out search for: " + lsid);

                    //     IndexedRecord[] ir = OccurrencesIndex.filterSpeciesRecords(vtn.getScientificname());
                    IndexedRecord[] ir = OccurrencesIndex.filterSpeciesRecords(lsid);
                    if (ir != null) {
                        if (ir != null) {
                            double[] points = OccurrencesIndex.getPoints(ir[0].record_start, ir[0].record_end);

                            System.out.println("HeatMap.baseDir: " + baseDir.getAbsolutePath());
                            //HeatMap hm = new HeatMap(baseDir, vtn.getScientificname());                            
                            HeatMap hm = new HeatMap(baseDir, lsid.replace(":", "_"));
                            if ((points.length / 2) < 500) {
                                hm.generatePoints(points);
                                hm.drawOuput(outputfile, false);
                                isHeatmap=false;
                            } else {
                                hm.generateClasses(points);
                                hm.drawOuput(outputfile, true);
                                isHeatmap=true; 
                            }

                            //msg = baseOutUrl + lsid.replace(":", "_") + ".png";
                            output = generateOutput(baseOutUrl + lsid.replace(":", "_") + ".png", baseOutUrl + "legend_" + lsid.replace(":", "_") + ".png", isHeatmap);
                            System.out.println("Sending out: " + msg);

                        } else {
                            //msg = "No species";
                            //msg = baseOutUrl + "base/mapaus1_white.png";
                            output = generateOutput("base/mapaus1_white.png", "", "blank");
                            System.out.println("Empty filter species");
                        }
                    } else {
                        //msg = baseOutUrl + "base/mapaus1_white.png";
                        output = generateOutput("base/mapaus1_white.png", "", "blank");
                    }
                }
            } else if (institutionUid != null) {
                //msg = baseOutUrl + process(baseDir, "institution_code_uid", institutionUid);
                output = process(baseDir, "institution_code_uid", institutionUid);
            } else if (collectionUid != null) {
                //msg = baseOutUrl + process(baseDir, "collection_code_uid", collectionUid);
                output = process(baseDir, "collection_code_uid", collectionUid);
            } else if (dataProviderUid != null) {
                //msg = baseOutUrl + process(baseDir, "data_provider_uid", dataProviderUid);
                output = process(baseDir, "data_provider_uid", dataProviderUid);
            } else if (dataResourceUid != null) {
                //msg = baseOutUrl + process(baseDir, "data_resource_uid", dataResourceUid);
                output = process(baseDir, "data_resource_uid", dataResourceUid);
            } else if (tcid != null) {
                //msg = baseOutUrl + process(baseDir, "taxonConceptId", tcid);
                output = process(baseDir, "taxonConceptId", tcid);
            } else if (spname != null) {
                System.out.println("Mapping via speciesname: " + spname);
                String outputfile = baseDir + File.separator + spname + ".png";
                //IndexedRecord[] ir = OccurrencesIndex.filterSpeciesRecords(spname);
                IndexedRecord[] ir = null;
                if (ir != null) {
                    double[] points = OccurrencesIndex.getPoints(ir[0].record_start, ir[0].record_end);

                    System.out.println("HeatMap.baseDir: " + baseDir.getAbsolutePath());
                    HeatMap hm = new HeatMap(baseDir, spname);
                    if ((points.length / 2) < 500) {
                        hm.generatePoints(points);
                        hm.drawOuput(outputfile, false);
                        isHeatmap = false;
                    } else {
                        hm.generateClasses(points);
                        hm.drawOuput(outputfile, true);
                        isHeatmap = true;
                    }


                    //msg = baseOutUrl + spname + ".png";
                    //output = generateOutput(baseOutUrl + spname + ".png", baseOutUrl + "legend_" + spname + ".png", isHeatmap);
                    output = generateOutput(spname + ".png", "legend_" + spname + ".png", ((isHeatmap)?"heatmap":"points"));
                    System.out.println("Sending out: " + msg);

                } else {
                    System.out.println("getting " + spname + " from db");
                    List<Species> species = speciesDao.getRecordsById(spname);

                    if (species != null) {
                        double[] points = new double[species.size() * 2];
                        int pi = 0;
                        for (Species sp : species) {
                            System.out.println("sp: " + sp.getSpecies() + " at " + sp.getLongitude() + ", " + sp.getLatitude());
                            points[pi] = Double.parseDouble(sp.getLongitude());
                            points[pi + 1] = Double.parseDouble(sp.getLatitude());
                            pi += 2;
                        }
                        HeatMap hm = new HeatMap(baseDir, spname);
                        if ((points.length / 2) < 500) {
                            hm.generatePoints(points);
                            hm.drawOuput(outputfile, false);
                            isHeatmap = false;
                        } else {
                            hm.generateClasses(points);
                            hm.drawOuput(outputfile, true);
                            isHeatmap = true;
                        }

                        //msg = baseOutUrl + spname + ".png";
                        //output = generateOutput(baseOutUrl + spname + ".png", baseOutUrl + "legend_" + spname + ".png", isHeatmap);
                        output = generateOutput(spname + ".png", "legend_" + spname + ".png", ((isHeatmap)?"heatmap":"points"));
                        System.out.println("Sending out: " + msg);

                    } else {
                        //msg = baseOutUrl + "base/mapaus1_white.png";
                        output = generateOutput("base/mapaus1_white.png", "", "blank");
                        System.out.println("Empty filter species");
                    }


                    //msg = "No species";
                    //msg = "base/mapaus1_white.png";
                    //System.out.println("Empty filter species");
                }

            }

        } catch (Exception ex) {
            Logger.getLogger(WMSController.class.getName()).log(Level.SEVERE, null, ex);
        }
//        try {
//            response.sendRedirect(msg);
//        } catch (IOException ex) {
//            Logger.getLogger(WMSController.class.getName()).log(Level.SEVERE, null, ex);
//        }

        return output;
    }

    private int[] joinIntArrays(int[] a, int[] b) {
        try {

            int[] finalArray = Arrays.copyOf(a, a.length + b.length);
            System.arraycopy(b, 0, finalArray, a.length, b.length);
            return finalArray;
        } catch (Exception e) {
            System.out.println("Error joining arrays: ");
            e.printStackTrace(System.out);
        }

        return null;
    }

    private void writeToFile(String filename, int[] data) {
        try {
            BufferedWriter out = new BufferedWriter(new FileWriter(filename));
            for (int i = 0; i < data.length; i++) {
                out.write(data[i] + "\n");
            }

            out.close();
        } catch (IOException e) {
        }
    }

    private int[] getMatchingRecs(int[] source, int[] subset) {
        try {
            int[] tmpArray = new int[source.length];
            int count = 0;

            for (int i = 0; i < subset.length; i++) {
                if (Arrays.binarySearch(source, subset[i]) > 0) {
                    tmpArray[count] = subset[i];
                    count++;
                }
            }

            System.out.println("final count: " + count);

            int[] finalArray = new int[count];
            System.arraycopy(tmpArray, 0, finalArray, 0, finalArray.length);
            return finalArray;

        } catch (Exception e) {
            System.out.println("Error getting matching records:");
            e.printStackTrace(System.out);
        }

        return null;
    }

    private Map process(File baseDir, String key, String value) {
        String msg = "";
        Map output = new HashMap();
        boolean isHeatmap = true;
        //String baseOutUrl = TabulationSettings.base_output_url + "/output/sampling/";

        String outputfile = baseDir + File.separator + value + ".png";
        System.out.println("Checking if already present: " + outputfile);
        File imgFile = new File(outputfile);
        if (imgFile.exists()) {
            System.out.println("File already present, sending that: " + value + ".png");
            //msg = value + ".png";
            //output = generateOutput(baseOutUrl + value + ".png", baseOutUrl + "legend_" + value + ".png", isHeatmap);
            output = generateOutput(value + ".png", "legend_" + value + ".png", "");
        } else {
            System.out.println("Starting out search for: " + value);
            //msg = "generating density data_provider_id map for: " + value;
            int[] recs = OccurrencesIndex.lookup(key, value);
            if (recs != null) {
                int[] finalRecs = recs;

                double[][] pts = OccurrencesIndex.getPointsPairs();

                double[] points = new double[finalRecs.length * 2];
                for (int i = 0; i < finalRecs.length * 2; i += 2) {
                    points[i] = pts[finalRecs[i / 2]][0];
                    points[i + 1] = pts[finalRecs[i / 2]][1];
                }
                System.out.println("HeatMap.baseDir: " + baseDir.getAbsolutePath());
                HeatMap hm = new HeatMap(baseDir, value);
                if ((points.length / 2) < 500) {
                    hm.generatePoints(points);
                    hm.drawOuput(outputfile, false);
                    isHeatmap = false;
                } else {
                    hm.generateClasses(points);
                    hm.drawOuput(outputfile, true);
                    isHeatmap = true;
                }

                //msg = value + ".png";
                output = generateOutput(value + ".png", "legend_" + value + ".png", ((isHeatmap)?"heatmap":"points"));
            } else {
                //msg = "base/mapaus1_white.png";
                output = generateOutput("base/mapaus1_white.png", "", "blank");
            }
            System.out.println("Sending out: " + msg);
        }

        return output;

    }

    public static String getBaseUrl(HttpServletRequest request) {
        if ((request.getServerPort() == 80)
                || (request.getServerPort() == 443)) {
            return request.getScheme() + "://"
                    + request.getServerName()
                    + request.getContextPath();
        } else {
            return request.getScheme() + "://"
                    + request.getServerName() + ":" + request.getServerPort()
                    + request.getContextPath();
        }
    }

    private Map generateOutput(String mapUrl, String legUrl, String type) {

        File baseDir = new File(baseOutputPath + "output" + File.separator + "sampling" + File.separator);

        // check if the legend file exists,
        // if so, then its a heatmap
        // else it's a point map
        if (!legUrl.equalsIgnoreCase("")) {
            String legfile = baseDir + File.separator + "legend_" + mapUrl;
            File legFile = new File(legfile);
            if (legFile.exists()) {
                legUrl = "";
                type = "heatmap";
            } else {
                legUrl = baseOutUrl + legUrl;
                type = "points";
            }
        }

//        if (type.equalsIgnoreCase("")) {
//            type = "points";
//        }
        Map output = new HashMap();
        output.put("mapUrl", mapUrl);
        output.put("legendUrl", legUrl);
        output.put("type", type);

        return output;
    }

    private Map generateOutput(String mapUrl, String legUrl, boolean heatmap) {

        Map output = new HashMap();

//        StringBuffer output = new StringBuffer();
//
//        output.append("{");
//
//        // the map image url
//        output.append("mapUrl:");
//        output.append("'").append(mapUrl).append("'");
//
//        output.append(",");
//
//        // the legend image url
//        output.append("legendUrl:");
//        output.append("'").append(legUrl).append("'");
//
//        output.append(",");
//
//        output.append("type:");
//        if (heatmap) {
//            output.append("'heatmap'");
//        } else {
//            output.append("'points'");
//        }
//
//        output.append("}");

        String type = "heatmap";
        if (!heatmap) {
            type = "points";
            legUrl = ""; 
        }
        output.put("mapUrl", mapUrl);
        output.put("legendUrl", legUrl);
        output.put("type", type);

        return output;
    }

    public String generateMapLSID(String lsid) {


        String value = lsid;

        List<Species> species = speciesDao.getRecordsById(lsid);
        System.out.println("Found " + species.size() + " records via db");
        double[] points = null;
        String outputfile = "";
        File baseDir = null;
        for (Species sp : species) {
        }

        HeatMap hm = new HeatMap(baseDir, value);
        if ((points.length / 2) < 500) {
            hm.generatePoints(points);
            hm.drawOuput(outputfile, false);
        } else {
            hm.generateClasses(points);
            hm.drawOuput(outputfile, true);
        }

        return value + ".png";

    }

    /**
     * Action call to get a layer info based on it's ID
     *
     * @param id
     * @return LayerInfo layer
     */
    /*
    //@RequestMapping(method = RequestMethod.GET)
    public
    @ResponseBody
    String getSpeciesDensityMap(@RequestParam String species_lsid) {
    System.out.print("generating density map for: " + species_lsid);
    return "generating density map for: " + species_lsid;
    }

    //@RequestMapping(method = RequestMethod.GET)
    public
    @ResponseBody
    String getFamilyDensityMap(@RequestParam String family_lsid) {
    System.out.print("generating density map for: " + family_lsid);
    return "generating density map for: " + family_lsid;
    }
     * 
     */
    public static void main(String[] args) {
    }
}