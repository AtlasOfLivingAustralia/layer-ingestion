package org.ala.spatial.util;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.Serializable;
import java.util.HashMap;
import javax.imageio.ImageIO;
import org.ala.spatial.analysis.cluster.SpatialCluster3;

/**
 * SimpleRegion enables point to shape intersections, where the shape
 * is stored within SimpleRegion as a circle, bounding box or polygon.
 *
 * Other utilities include shape presence on a defined grid;
 * fully present, partially present, absent.
 *
 * @author Adam Collins
 */
public class SimpleRegion extends Object implements Serializable {

    static final long serialVersionUID = -5509351896749940566L;
    /**
     * shape type not declared
     */
    public static final int UNDEFINED = 0;
    /**
     * shape type bounding box; upper, lower, left, right
     */
    public static final int BOUNDING_BOX = 1;
    /**
     * shape type circle; point and radius
     */
    public static final int CIRCLE = 2;
    /**
     * shape type polygon; list of points as longitude, latitude pairs
     * last point == first point
     */
    public static final int POLYGON = 3;
    /**
     * UNDEFINED state for grid intersection output
     *
     * can be considered ABSENCE
     */
    public static final int GI_UNDEFINED = 0;
    /**
     * PARTiALLy PRESENT state for grid intersection output
     */
    public static final int GI_PARTIALLY_PRESENT = 1;
    /**
     * FULLY PRESENT state for grid intersection output
     */
    public static final int GI_FULLY_PRESENT = 2;
    /**
     * ABSENCE state for grid intersection output
     */
    public static final int GI_ABSENCE = 0;
    /**
     * assigned shape type
     */
    int type;
    /**
     * points store
     * BOUNDING_BOX = double [2][2]
     * CIRCLE = double [1][2]
     * POLYGON, n points (start = end) = double[n][2]
     */
    float[][] points;
    /**
     * for point/grid to polygon intersection method
     *
     * polygon edges as lines of the form <code>y = a*x + b</code>
     * lines = double [n][2]
     * where
     * 	n is number of edges
     * 	value at [0] is <code>a</code>
     * 	value at [1] is <code>b</code>
     */
    float[][] lines2;
    /**
     * bounding box for types BOUNDING_BOX and POLYGON
     *
     * bounding_box = double [2][2]
     * where
     * 	[0][0] = minimum longitude
     *  [0][1] = minimum latitude
     *  [1][0] = maximum longitude
     *  [1][1] = maximum latitude
     */
    double[][] bounding_box; //for polygons
    /**
     * radius for type CIRCLE in m
     *
     */
    double radius;
    /**
     * misc attributes
     */
    HashMap<String, Object> attributes;

    /**
     * Constructor for a SimpleRegion with no shape
     */
    public SimpleRegion() {
        type = UNDEFINED;
    }

    /**
     * gets number of points for type POLYGON
     *
     * note: first point = last point
     *
     * @return number of points as int
     */
    public int getNumberOfPoints() {
        return points.length;
    }

    /**
     * gets the bounding box for types POLYGON and BOUNDING_BOX
     *
     * @return bounding box as double[2][2]
     * with [][0] longitude and [][1] latitude
     * minimum values at [0][], maximum values at [1][0]
     */
    public double[][] getBoundingBox() {
        return bounding_box;
    }

    /**
     * defines the SimpleRegion as type BOUNDING_BOX
     *
     * @param longitude1
     * @param latitude1
     * @param longitude2
     * @param latitude2
     */
    public void setBox(double longitude1, double latitude1, double longitude2, double latitude2) {
        type = BOUNDING_BOX;
        points = new float[2][2];
        points[0][0] = (float) Math.min(longitude1, longitude2);
        points[0][1] = (float) Math.min(latitude1, latitude2);
        points[1][0] = (float) Math.max(longitude1, longitude2);
        points[1][1] = (float) Math.max(latitude1, latitude2);

        for (int i = 0; i < points.length; i++) {
            //fix at -180 and 180
            if (points[i][0] < -180) {
                points[i][0] = -180;
            }
            if (points[i][0] > 180) {
                points[i][0] = 180;
            }
            while (points[i][1] < -180) {
                points[i][1] = -180;
            }
            while (points[i][1] > 180) {
                points[i][1] = 180;
            }
        }

        bounding_box = new double[2][2];
        bounding_box[0][0] = points[0][0];
        bounding_box[0][1] = points[0][1];
        bounding_box[1][0] = points[1][0];
        bounding_box[1][1] = points[1][1];
    }

    /**
     * defines the SimpleRegion as type UNDEFINED
     */
    public void setNone() {
        type = UNDEFINED;
    }

    /**
     * defines the SimpleRegion as type CIRCLE
     *
     * @param longitude
     * @param latitude
     * @param radius_ radius of the circle in m
     */
    public void setCircle(double longitude, double latitude, double radius_) {
        type = CIRCLE;
        points = new float[1][2];
        points[0][0] = (float) longitude;
        points[0][1] = (float) latitude;
        radius = radius_;
    }

    /**
     * defines the SimpleRegion as type POLYGON
     *
     * @param points_ array of points as longitude and latiude
     * in double [n][2] where n is the number of points
     */
    public void setPolygon(double[][] points_) {
        if (points_ != null && points_.length > 1) {
            type = POLYGON;
            int i;

            for (i = 0; i < points_.length; i++) {
                //fix at -180 and 180
                if (points_[i][0] < -180) {
                    points_[i][0] = -180;
                }
                if (points_[i][0] > 180) {
                    points_[i][0] = 180;
                }
                while (points_[i][1] < -180) {
                    points_[i][1] = -180;
                }
                while (points_[i][1] > 180) {
                    points_[i][1] = 180;
                }
            }

            /* copy and ensure last point == first point */
            int len = points_.length - 1;
            if (points_[0][0] != points_[len][0] || points_[0][1] != points_[len][1]) {
                points = new float[points_.length + 1][2];
                for (i = 0; i < points_.length; i++) {
                    points[i][0] = (float) points_[i][0];
                    points[i][1] = (float) points_[i][1];
                }
                points[points_.length][0] = (float) points_[0][0];
                points[points_.length][1] = (float) points_[0][1];
            } else {
                points = new float[points_.length][2];
                for (i = 0; i < points_.length; i++) {
                    points[i][0] = (float) points_[i][0];
                    points[i][1] = (float) points_[i][1];
                }
            }

            /* bounding box setup */
            bounding_box = new double[2][2];
            bounding_box[0][0] = points[0][0];
            bounding_box[0][1] = points[0][1];
            bounding_box[1][0] = points[0][0];
            bounding_box[1][1] = points[0][1];
            for (i = 1; i < points.length; i++) {
                if (bounding_box[0][0] > points[i][0]) {
                    bounding_box[0][0] = points[i][0];
                }
                if (bounding_box[1][0] < points[i][0]) {
                    bounding_box[1][0] = points[i][0];
                }
                if (bounding_box[0][1] > points[i][1]) {
                    bounding_box[0][1] = points[i][1];
                }
                if (bounding_box[1][1] < points[i][1]) {
                    bounding_box[1][1] = points[i][1];
                }
            }

            // intersection method precalculated data
            lines2 = new float[points.length][2];   //lines[0][] is not used
            for (i = 0; i < points.length - 1; i++) {
                lines2[i + 1][0] = (points[i][1] - points[i + 1][1])
                        / (points[i][0] - points[i + 1][0]);				//slope
                lines2[i + 1][1] = points[i][1] - lines2[i + 1][0] * points[i][0];		//intercept
            }
        }
    }

    /**
     * defines the SimpleRegion as type POLYGON
     *
     * @param points_ array of points as longitude and latiude
     * in double [n][2] where n is the number of points
     */
    public void setPolygon(float[][] points_) {
        if (points_ != null && points_.length > 1) {
            type = POLYGON;
            int i;

            for (i = 0; i < points_.length; i++) {
                //fix at -180 and 180
                if (points_[i][0] < -180) {
                    points_[i][0] = -180;
                }
                if (points_[i][0] > 180) {
                    points_[i][0] = 180;
                }
                while (points_[i][1] < -180) {
                    points_[i][1] = -180;
                }
                while (points_[i][1] > 180) {
                    points_[i][1] = 180;
                }
            }

            /* copy and ensure last point == first point */
            int len = points_.length - 1;
            if (points_[0][0] != points_[len][0] || points_[0][1] != points_[len][1]) {
                points = new float[points_.length + 1][2];
                for (i = 0; i < points_.length; i++) {
                    points[i][0] = (float) points_[i][0];
                    points[i][1] = (float) points_[i][1];
                }
                points[points_.length][0] = (float) points_[0][0];
                points[points_.length][1] = (float) points_[0][1];
            } else {
                points = new float[points_.length][2];
                for (i = 0; i < points_.length; i++) {
                    points[i][0] = (float) points_[i][0];
                    points[i][1] = (float) points_[i][1];
                }
            }

            /* bounding box setup */
            bounding_box = new double[2][2];
            bounding_box[0][0] = points[0][0];
            bounding_box[0][1] = points[0][1];
            bounding_box[1][0] = points[0][0];
            bounding_box[1][1] = points[0][1];
            for (i = 1; i < points.length; i++) {
                if (bounding_box[0][0] > points[i][0]) {
                    bounding_box[0][0] = points[i][0];
                }
                if (bounding_box[1][0] < points[i][0]) {
                    bounding_box[1][0] = points[i][0];
                }
                if (bounding_box[0][1] > points[i][1]) {
                    bounding_box[0][1] = points[i][1];
                }
                if (bounding_box[1][1] < points[i][1]) {
                    bounding_box[1][1] = points[i][1];
                }
            }

            // intersection method precalculated data
            lines2 = new float[points.length][2];   //lines[0][] is not used
            for (i = 0; i < points.length - 1; i++) {
                lines2[i + 1][0] = (points[i][1] - points[i + 1][1])
                        / (points[i][0] - points[i + 1][0]);				//slope
                lines2[i + 1][1] = points[i][1] - lines2[i + 1][0] * points[i][0];		//intercept
            }
        }
    }

    /**
     * gets points of a polygon only
     *
     * @return points of this object if it is a polygon as double[][]
     * otherwise returns null.
     */
    public float[][] getPoints() {
        return points;
    }

    /**
     * returns true when the point provided is within the SimpleRegion
     *
     * note: type UNDEFINED implies no boundary, always returns true.
     *
     * @param longitude
     * @param latitude
     * @return true iff point is within or on the edge of this SimpleRegion
     */
    public boolean isWithin(double longitude, double latitude) {
        switch (type) {
            case 0:
                /* no region defined, must be within this absence of a boundary */
                return true;
            case 1:
                /* return for bounding box */
                return (longitude <= points[1][0] && longitude >= points[0][0]
                        && latitude <= points[1][1] && latitude >= points[0][1]);
            case 2:
                /* TODO: fix to use radius units m not degrees */
                double x = longitude - points[0][0];
                double y = latitude - points[0][1];
                return Math.sqrt(x * x + y * y) <= radius;
            case 3:
                /* determine for Polygon */
                return isWithinPolygon(longitude, latitude);
        }
        return false;
    }

    public boolean isWithin_EPSG900913(double longitude, double latitude) {
        switch (type) {
            case 0:
                /* no region defined, must be within this absence of a boundary */
                return true;
            case 1:
                /* return for bounding box */
                return (longitude <= points[1][0] && longitude >= points[0][0]
                        && latitude <= points[1][1] && latitude >= points[0][1]);
            case 2:
                /* TODO: fix to use radius units m not degrees */
                double x = longitude - points[0][0];
                double y = latitude - points[0][1];
                return Math.sqrt(x * x + y * y) <= radius;
            case 3:
                /* determine for Polygon */
                return isWithinPolygon_EPSG900913(longitude, latitude);
        }
        return false;
    }

    /**
     * returns true when point is within the polygon
     *
     * method:
     * treat as segments with target long in the middle:
     *
     *
     *	              __-1__|___1_
     *			    |
     *
     *
     * iterate through points and count number of latitude axis crossing where
     * crossing is > latitude.
     *
     * point is inside of area when number of crossings is odd;
     *
     * point is on a polygon edge return true
     *
     * @param longitude
     * @param latitude
     * @return true iff longitude and latitude point is on edge or within polygon
     */
    private boolean isWithinPolygon(double longitude, double latitude) {
        // bounding box test
        if (longitude <= bounding_box[1][0] && longitude >= bounding_box[0][0]
                && latitude <= bounding_box[1][1] && latitude >= bounding_box[0][1]) {

            //initial segment
            boolean segment = points[0][0] > longitude;

            double y;
            int i;
            int len = points.length;
            int score = 0;

            for (i = 1; i < len; i++) {
                // is it in a new segment?
                if ((points[i][0] > longitude) != segment) {
                    //lat value at line crossing > target point
                    y = lines2[i][0] * longitude + lines2[i][1];
                    if (y > latitude) {
                        score++;
                    } else if (y == latitude) {
                        //line crossing
                        return true;
                    }

                    segment = !segment;
                } else if (points[i][0] == longitude && points[i][1] == latitude) {
                    //point on point
                    return true;
                }
            }
            return (score % 2 != 0);
        }
        return false;		//not within bounding box
    }

    private boolean isWithinPolygon_EPSG900913(double longitude, double latitude) {
        SpatialCluster3 sc = new SpatialCluster3();

        // bounding box test
        if (longitude <= bounding_box[1][0] && longitude >= bounding_box[0][0]
                && latitude <= bounding_box[1][1] && latitude >= bounding_box[0][1]) {

            //initial segment
            int longitudePx = sc.convertLngToPixel(longitude);
            boolean segment = sc.convertLngToPixel(points[0][0]) > longitudePx;

            int y;
            int i;
            int len = points.length;
            int score = 0;

            for (i = 1; i < len; i++) {
                // is it in a new segment?
                if ((sc.convertLngToPixel(points[i][0]) > longitudePx) != segment) {
                    //lat value at line crossing > target point
                    y = (int)((longitudePx - sc.convertLngToPixel(points[i][0]))
                            * ((sc.convertLatToPixel(points[i][1]) - sc.convertLatToPixel(points[i - 1][1]))
                            / (double) (sc.convertLngToPixel(points[i][0]) - sc.convertLngToPixel(points[i - 1][0])))
                            + sc.convertLatToPixel(points[i][1]));
                    if (y > sc.convertLatToPixel(latitude)) {
                        score++;
                    } else if (y == sc.convertLatToPixel(latitude)) {
                        //line crossing
                        return true;
                    }

                    segment = !segment;
                } else if (points[i][0] == longitude && points[i][1] == latitude) {
                    //point on point
                    return true;
                }
            }
            return (score % 2 != 0);
        }
        return false;		//not within bounding box
    }

    /**
     * determines overlap with a grid
     *
     * for type POLYGON
     * when <code>three_state_map</code> is not null populate it with one of:
     * 	GI_UNDEFINED
     * 	GI_PARTIALLY_PRESENT
     * 	GI_FULLY_PRESENT
     * 	GI_ABSENCE
     *
     * @param longitude1
     * @param latitude1
     * @param longitude2
     * @param latitude2
     * @param xres number of longitude segements as int
     * @param yres number of latitude segments as int
     * @return (x,y) as double [][2] for each grid cell at least partially falling
     * within the specified region of the specified resolution beginning at 0,0
     * for minimum longitude and latitude through to xres,yres for maximums
     */
    public int[][] getOverlapGridCells(double longitude1, double latitude1, double longitude2, double latitude2, int width, int height, byte[][] three_state_map, boolean noCellsReturned) {
        int[][] cells = null;
        switch (type) {
            case 0:
                break;
            case 1:
                cells = getOverlapGridCells_Box(longitude1, latitude1, longitude2, latitude2, width, height, bounding_box, three_state_map, noCellsReturned);
                break;
            case 2:
                break; /* TODO: circle grid */
            case 3:
                cells = getOverlapGridCells_Polygon(longitude1, latitude1, longitude2, latitude2, width, height, three_state_map, noCellsReturned);
        }

        return cells;
    }

    /**
     * stacks PARTIALLY_PRESENT shape outline onto three_state_map
     * 
     * @param longitude1
     * @param latitude1
     * @param longitude2
     * @param latitude2
     * @param width
     * @param height
     * @param three_state_map
     * @param noCellsReturned
     */
    public void getOverlapGridCells_Acc(double longitude1, double latitude1, double longitude2, double latitude2, int width, int height, byte[][] three_state_map) {
        switch (type) {
            case 0:
                break;
            case 1:
                getOverlapGridCells_Box_Acc(longitude1, latitude1, longitude2, latitude2, width, height, bounding_box, three_state_map);
                break;
            case 2:
                break; /* TODO: circle grid */
            case 3:
                getOverlapGridCells_Polygon_Acc(longitude1, latitude1, longitude2, latitude2, width, height, three_state_map);
        }
    }

    public int[][] getOverlapGridCells(double longitude1, double latitude1, double longitude2, double latitude2, int width, int height, byte[][] three_state_map) {
        return getOverlapGridCells(longitude1, latitude1, longitude2, latitude2, width, height, three_state_map, false);
    }

    /**
     * determines overlap with a grid for a BOUNDING_BOX
     *
     * @param longitude1
     * @param latitude1
     * @param longitude2
     * @param latitude2
     * @param xres number of longitude segements as int
     * @param yres number of latitude segments as int
     * @param bb bounding box as double[2][2] with [][0] as longitude, [][1] as latitude,
     * [0][] as minimum values, [1][] as maximum values
     * @return (x,y) as double [][2] for each grid cell at least partially falling
     * within the specified region of the specified resolution beginning at 0,0
     * for minimum longitude and latitude through to xres,yres for maximums
     */
    public int[][] getOverlapGridCells_Box(double longitude1, double latitude1,
            double longitude2, double latitude2, int width, int height, double[][] bb, byte[][] three_state_map, boolean noCellsReturned) {

        double xstep = Math.abs(longitude2 - longitude1) / (double) width;
        double ystep = Math.abs(latitude2 - latitude1) / (double) height;

        //double maxlong = Math.max(longitude1, longitude2);
        double minlong = Math.min(longitude1, longitude2);
        //double maxlat = Math.max(latitude1, latitude2);
        double minlat = Math.min(latitude1, latitude2);

        //setup minimums from bounding box (TODO: should this have -1 on steps?)
        int xstart = (int) Math.floor((bb[0][0] - minlong) / xstep);
        int ystart = (int) Math.floor((bb[0][1] - minlat) / ystep);
        int xend = (int) Math.ceil((bb[1][0] - minlong) / xstep);
        int yend = (int) Math.ceil((bb[1][1] - minlat) / ystep);
        if (xstart < 0) {
            xstart = 0;
        }
        if (ystart < 0) {
            ystart = 0;
        }
        if (xend > width) {
            xend = width;
        }
        if (yend > height) {
            yend = height;
        }

        // fill data with cell coordinates
        int out_width = xend - xstart;
        int out_height = yend - ystart;
        int j, i, p = 0;
        int[][] data = null;
        if (!noCellsReturned) {
            data = new int[out_width * out_height][2];
            if (three_state_map == null) {
                for (j = ystart; j < yend; j++) {
                    for (i = xstart; i < xend; i++) {
                        data[p][0] = i;
                        data[p][1] = j;
                        p++;
                    }
                }
            } else {
                for (j = ystart; j < yend; j++) {
                    for (i = xstart; i < xend; i++) {
                        data[p][0] = i;
                        data[p][1] = j;
                        three_state_map[j][i] = SimpleRegion.GI_FULLY_PRESENT;
                        p++;
                    }
                }
                //set three state map edges to partially present
                if (xstart < xend && xend > 0) {
                    for (j = ystart; j < yend; j++) {
                        three_state_map[j][xstart] = SimpleRegion.GI_PARTIALLY_PRESENT;
                        three_state_map[j][xend - 1] = SimpleRegion.GI_PARTIALLY_PRESENT;
                    }
                }
                if (ystart < yend && yend > 0) {
                    for (i = xstart; i < xend; i++) {
                        three_state_map[ystart][i] = SimpleRegion.GI_PARTIALLY_PRESENT;
                        three_state_map[yend - 1][i] = SimpleRegion.GI_PARTIALLY_PRESENT;
                    }
                }

                //no need to set SimpleRegion.GI_ABSENCE
            }
        } else {
            if (three_state_map == null) {
                for (j = ystart; j < yend; j++) {
                    for (i = xstart; i < xend; i++) {
                        data[p][0] = i;
                        data[p][1] = j;
                        p++;
                    }
                }
            } else {
                for (j = ystart; j < yend; j++) {
                    for (i = xstart; i < xend; i++) {
                        three_state_map[j][i] = SimpleRegion.GI_FULLY_PRESENT;
                    }
                }
                //set three state map edges to partially present
                if (xstart < xend && xend > 0) {
                    for (j = ystart; j < yend; j++) {
                        three_state_map[j][xstart] = SimpleRegion.GI_PARTIALLY_PRESENT;
                        three_state_map[j][xend - 1] = SimpleRegion.GI_PARTIALLY_PRESENT;
                    }
                }
                if (ystart < yend && yend > 0) {
                    for (i = xstart; i < xend; i++) {
                        three_state_map[ystart][i] = SimpleRegion.GI_PARTIALLY_PRESENT;
                        three_state_map[yend - 1][i] = SimpleRegion.GI_PARTIALLY_PRESENT;
                    }
                }
            }
        }
        return data;
    }

    /**
     * defines a region by a points string, POLYGON only
     *
     * TODO: define better format for parsing, including BOUNDING_BOX and CIRCLE
     *
     * @param pointsString points separated by ',' with longitude and latitude separated by ':'
     * @return SimpleRegion object
     */
    public static SimpleRegion parseSimpleRegion(String pointsString) {
        if (pointsString.equalsIgnoreCase("none")) {
            return null;
        }
        SimpleRegion simpleregion = new SimpleRegion();
        String[] pairs = pointsString.split(",");

        double[][] points = new double[pairs.length][2];
        for (int i = 0; i < pairs.length; i++) {
            String[] longlat = pairs[i].split(":");
            if (longlat.length == 2) {
                try {
                    points[i][0] = Double.parseDouble(longlat[0]);
                    points[i][1] = Double.parseDouble(longlat[1]);
                } catch (Exception e) {
                    //TODO: alert failure
                }
            } else {
                //TODO: alert failure
            }
        }

        //test for box
        //  get min/max long/lat
        //  each point has only one identical lat or long to previous point
        //  4 or 5 points (start and end points may be identical)
        if (((points.length == 4 && (points[0][0] != points[3][0] || points[0][1] != points[3][1]))
                || (points.length == 5 && points[0][0] == points[4][0]
                && points[0][1] == points[4][1]))) {

            //get min/max long/lat
            double minlong = 0, minlat = 0, maxlong = 0, maxlat = 0;
            for (int i = 0; i < points.length; i++) {
                if (i == 0 || minlong > points[i][0]) {
                    minlong = points[i][0];
                }
                if (i == 0 || maxlong < points[i][0]) {
                    maxlong = points[i][0];
                }
                if (i == 0 || minlat > points[i][1]) {
                    minlat = points[i][1];
                }
                if (i == 0 || maxlat < points[i][1]) {
                    maxlat = points[i][1];
                }
            }

            //  each point has only one identical lat or long to previous point
            int prev_idx = 3;
            int i = 0;
            for (i = 0; i < 4; i++) {
                if ((points[i][0] == points[prev_idx][0])
                        == (points[i][1] == points[prev_idx][1])) {
                    break;
                }
                prev_idx = i;
            }
            //it is a box if no 'break' occurred
            if (i == 4) {
                simpleregion.setBox(minlong, minlat, maxlong, maxlat);
                return simpleregion;
            }
        }
        simpleregion.setPolygon(points);
        return simpleregion;
    }

    public double getWidth() {
        return bounding_box[1][0] - bounding_box[0][0];
    }

    public double getHeight() {
        return bounding_box[1][1] - bounding_box[0][1];
    }

    public int getType() {
        return type;
    }

    public void setAttribute(String name, Object value) {
        if (attributes == null) {
            attributes = new HashMap<String, Object>();
        }
        attributes.put(name, value);
    }

    public Object getAttribute(String name) {
        if (attributes != null) {
            return attributes.get(name);
        }
        return null;
    }

    public void saveGridAsImage(byte[][] three_state_map) {
        try {
            long t1 = System.currentTimeMillis();
            BufferedImage bi = new BufferedImage(three_state_map[0].length, three_state_map.length, BufferedImage.TYPE_INT_RGB);
            for (int i = 0; i < three_state_map.length; i++) {
                for (int j = 0; j < three_state_map[i].length; j++) {
                    if (three_state_map[i][j] == 0) {
                        bi.setRGB(j, (three_state_map.length - 1 - i), 0xFFFFFF);
                    } else if (three_state_map[i][j] == 1) {
                        bi.setRGB(j, (three_state_map.length - 1 - i), 0x99FF99);
                    } else if (three_state_map[i][j] == 2) {
                        bi.setRGB(j, (three_state_map.length - 1 - i), 0x9999FF);
                    }
                }
            }

            ImageIO.write(bi, "png", File.createTempFile("grd", ".png", new File("d:\\")));
            System.out.println("save grid in " + (System.currentTimeMillis() - t1) + "ms");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * determines overlap with a grid for POLYGON
     *
     * when <code>three_state_map</code> is not null populate it with one of:
     * 	GI_UNDEFINED
     * 	GI_PARTIALLY_PRESENT
     * 	GI_FULLY_PRESENT
     * 	GI_ABSENCE
     *
     * 1. Get 3state mask and fill edge passes as 'partial'.
     *  then
     * 3. Test 0,0 then progress across vert raster until finding cells[][] entry
     * 4. Repeat from (3).
     *
     * @param longitude1
     * @param latitude1
     * @param longitude2
     * @param latitude2
     * @param xres number of longitude segements as int
     * @param yres number of latitude segments as int
     * @return (x,y) as double [][2] for each grid cell at least partially falling
     * within the specified region of the specified resolution beginning at 0,0
     * for minimum longitude and latitude through to xres,yres for maximums
     */
    public int[][] getOverlapGridCells_Polygon(double longitude1, double latitude1, double longitude2, double latitude2, int width, int height, byte[][] three_state_map, boolean noCellsReturned) {
        int i, j;
        if (three_state_map == null) {
            three_state_map = new byte[height][width];
        }

        double divx = (longitude2 - longitude1) / width;
        double divy = (latitude2 - latitude1) / height;

        //to cells
        int x, y, xend, yend, xDirection, icross;
        double xcross, endlat, dx1, dx2, dy1, dy2, slope, intercept;
        for (j = 1; j < points.length; j++) {
            if (points[j][1] < points[j - 1][1]) {
                dx1 = points[j][0];
                dy1 = points[j][1];
                dx2 = points[j - 1][0];
                dy2 = points[j - 1][1];
            } else {
                dx2 = points[j][0];
                dy2 = points[j][1];
                dx1 = points[j - 1][0];
                dy1 = points[j - 1][1];
            }
            x = (int) ((dx1 - longitude1) / divx);
            y = (int) ((dy1 - latitude1) / divy);
            xend = (int) ((dx2 - longitude1) / divx);
            yend = (int) ((dy2 - latitude1) / divy);

            if (y >= 0 && y < height && x >= 0 && x < width) {
                three_state_map[y][x] = GI_PARTIALLY_PRESENT;
            }

            if (x == xend && y == yend) {
                continue;
            }

            xDirection = (x < xend) ? 1 : -1;

            slope = (dy1 - dy2) / (dx1 - dx2);
            intercept = dy1 - slope * dx1;

            if (x == xend) {
                //vertical line
                while (y != yend) {
                    y++;
                    if (y >= 0 && y < height && x >= 0 && x < width) {
                        three_state_map[y][x] = GI_PARTIALLY_PRESENT;
                    }
                }
            } else if (y == yend) {
                //horizontal line
                while (x != xend) {
                    x += xDirection;
                    if (y >= 0 && y < height && x >= 0 && x < width) {
                        three_state_map[y][x] = GI_PARTIALLY_PRESENT;
                    }
                }
            } else { //sloped line
                endlat = dy2;
                for (double k = (y + 1) * divy + latitude1; k < endlat; k += divy) {
                    //move in yDirection to get x
                    xcross = (k - intercept) / slope;
                    icross = (int) ((xcross - longitude1) / divx);

                    while (x != icross && x != xend) {
                        x += xDirection;
                        if (y >= 0 && y < height && x >= 0 && x < width) {
                            three_state_map[y][x] = GI_PARTIALLY_PRESENT;
                        }
                    }

                    if (y != yend) {
                        y++;
                        if (y >= 0 && y < height && x >= 0 && x < width) {
                            three_state_map[y][x] = GI_PARTIALLY_PRESENT;
                        }
                    }
                }

                //finish horizontal line
                while (x != xend) {
                    x += xDirection;
                    if (y >= 0 && y < height && x >= 0 && x < width) {
                        three_state_map[y][x] = GI_PARTIALLY_PRESENT;
                    }
                }
            }
        }

        //do raster check
        int[][] data = new int[width * height][2];
        boolean cellsReturned = !noCellsReturned;
        int p = 0;
        for (j = 0; j < three_state_map[0].length; j++) {
            for (i = 0; i < three_state_map.length; i++) {
                if (three_state_map[i][j] == GI_PARTIALLY_PRESENT) {
                    //if it is partially present, do nothing
                } else if ((j == 0 || three_state_map[i][j - 1] == GI_PARTIALLY_PRESENT)) {
                    if (i > 0
                            && (three_state_map[i - 1][j] == GI_FULLY_PRESENT
                            || three_state_map[i - 1][j] == GI_ABSENCE)) {
                        //use same as LHS
                        three_state_map[i][j] = three_state_map[i - 1][j];
                    } else if (isWithin(j * divx + divx / 2 + longitude1, i * divy + divy / 2 + latitude1)) {
                        //if the previous was partially present, test
                        three_state_map[i][j] = GI_FULLY_PRESENT;
                    } //else absent
                } else {
                    //if the previous was fully present, repeat
                    //if the previous was absent, repeat
                    three_state_map[i][j] = three_state_map[i][j - 1];
                }

                //apply to cells;
                if (cellsReturned && three_state_map[i][j] != GI_UNDEFINED) {   //undefined == absence
                    data[p][0] = j;
                    data[p][1] = i;
                    p++;
                }
            }
        }
        return java.util.Arrays.copyOfRange(data, 0, p);
    }

    public void getOverlapGridCells_Polygon_Acc(double longitude1, double latitude1, double longitude2, double latitude2, int width, int height, byte[][] three_state_map) {
        int i, j;

        double divx = (longitude2 - longitude1) / width;
        double divy = (latitude2 - latitude1) / height;

        //to cells
        int x, y, xend, yend, xDirection, icross;
        double xcross, endlat, dx1, dx2, dy1, dy2, slope, intercept;
        for (j = 1; j < points.length; j++) {
            if (points[j][1] < points[j - 1][1]) {
                dx1 = points[j][0];
                dy1 = points[j][1];
                dx2 = points[j - 1][0];
                dy2 = points[j - 1][1];
            } else {
                dx2 = points[j][0];
                dy2 = points[j][1];
                dx1 = points[j - 1][0];
                dy1 = points[j - 1][1];
            }
            x = (int) ((dx1 - longitude1) / divx);
            y = (int) ((dy1 - latitude1) / divy);
            xend = (int) ((dx2 - longitude1) / divx);
            yend = (int) ((dy2 - latitude1) / divy);

            if (x == xend && y == yend) {
                continue;
            }

            xDirection = (x < xend) ? 1 : -1;

            slope = (dy1 - dy2) / (dx1 - dx2);
            intercept = dy1 - slope * dx1;

            if (y >= 0 && y < height && x >= 0 && x < width) {
                three_state_map[y][x] = GI_PARTIALLY_PRESENT;
            }

            if (x == xend) {
                //vertical line
                while (y != yend) {
                    y++;
                    if (y >= 0 && y < height && x >= 0 && x < width) {
                        three_state_map[y][x] = GI_PARTIALLY_PRESENT;
                    }
                }
            } else if (y == yend) {
                //horizontal line
                while (x != xend) {
                    x += xDirection;
                    if (y >= 0 && y < height && x >= 0 && x < width) {
                        three_state_map[y][x] = GI_PARTIALLY_PRESENT;
                    }
                }
            } else { //sloped line
                endlat = dy2;
                for (double k = (y + 1) * divy + latitude1; k < endlat; k += divy) {
                    //move in yDirection to get x
                    xcross = (k - intercept) / slope;
                    icross = (int) ((xcross - longitude1) / divx);

                    while (x != icross && x != xend) {
                        x += xDirection;
                        if (y >= 0 && y < height && x >= 0 && x < width) {
                            three_state_map[y][x] = GI_PARTIALLY_PRESENT;
                        }
                    }

                    if (y != yend) {
                        y++;
                        if (y >= 0 && y < height && x >= 0 && x < width) {
                            three_state_map[y][x] = GI_PARTIALLY_PRESENT;
                        }
                    }
                }

                //finish horizontal line
                while (x != xend) {
                    x += xDirection;
                    if (y >= 0 && y < height && x >= 0 && x < width) {
                        three_state_map[y][x] = GI_PARTIALLY_PRESENT;
                    }
                }
            }
        }
    }

    public int[][] fillAccMask(double longitude1, double latitude1, double longitude2, double latitude2, int width, int height, byte[][] three_state_map, boolean noCellsReturned) {
        double divx = (longitude2 - longitude1) / width;
        double divy = (latitude2 - latitude1) / height;

        int i, j;
        //do raster check
        int[][] data = null;
        boolean cellsReturned = !noCellsReturned;
        if (cellsReturned) {
            data = new int[width * height][2];
        }
        int p = 0;
        for (j = 0; j < three_state_map[0].length; j++) {
            for (i = 0; i < three_state_map.length; i++) {
                if (three_state_map[i][j] == GI_PARTIALLY_PRESENT) {
                    //if it is partially present, do nothing
                } else if ((j == 0 || three_state_map[i][j - 1] == GI_PARTIALLY_PRESENT)) {
                    if (i > 0
                            && (three_state_map[i - 1][j] == GI_FULLY_PRESENT
                            || three_state_map[i - 1][j] == GI_ABSENCE)) {
                        //use same as LHS
                        three_state_map[i][j] = three_state_map[i - 1][j];
                    } else if (isWithin(j * divx + divx / 2 + longitude1, i * divy + divy / 2 + latitude1)) {
                        //if the previous was partially present, test
                        three_state_map[i][j] = GI_FULLY_PRESENT;
                    } //else absent
                } else {
                    //if the previous was fully present, repeat
                    //if the previous was absent, repeat
                    three_state_map[i][j] = three_state_map[i][j - 1];
                }

                //apply to cells;
                if (cellsReturned && three_state_map[i][j] != GI_UNDEFINED) {   //undefined == absence
                    data[p][0] = j;
                    data[p][1] = i;
                    p++;
                }
            }
        }
        if (data != null) {
            data = java.util.Arrays.copyOf(data, p);
        }
        return data;
    }

    private void getOverlapGridCells_Box_Acc(double longitude1, double latitude1, double longitude2, double latitude2, int width, int height, double[][] bb, byte[][] three_state_map) {
        double xstep = Math.abs(longitude2 - longitude1) / (double) width;
        double ystep = Math.abs(latitude2 - latitude1) / (double) height;

        //double maxlong = Math.max(longitude1, longitude2);
        double minlong = Math.min(longitude1, longitude2);
        //double maxlat = Math.max(latitude1, latitude2);
        double minlat = Math.min(latitude1, latitude2);

        //setup minimums from bounding box (TODO: should this have -1 on steps?)
        int xstart = (int) Math.floor((bb[0][0] - minlong) / xstep);
        int ystart = (int) Math.floor((bb[0][1] - minlat) / ystep);
        int xend = (int) Math.ceil((bb[1][0] - minlong) / xstep);
        int yend = (int) Math.ceil((bb[1][1] - minlat) / ystep);
        if (xstart < 0) {
            xstart = 0;
        }
        if (ystart < 0) {
            ystart = 0;
        }
        if (xend > width) {
            xend = width;
        }
        if (yend > height) {
            yend = height;
        }

        // fill data with cell coordinates
        //int out_width = xend - xstart;
        //int out_height = yend - ystart;
        int j, i, p = 0;
        //int[][] data = null;

        //set three state map edges to partially present
        if (xstart < xend && xend > 0) {
            for (j = ystart; j < yend; j++) {
                three_state_map[j][xstart] = SimpleRegion.GI_PARTIALLY_PRESENT;
                three_state_map[j][xend - 1] = SimpleRegion.GI_PARTIALLY_PRESENT;
            }
        }
        if (ystart < yend && yend > 0) {
            for (i = xstart; i < xend; i++) {
                three_state_map[ystart][i] = SimpleRegion.GI_PARTIALLY_PRESENT;
                three_state_map[yend - 1][i] = SimpleRegion.GI_PARTIALLY_PRESENT;
            }
        }
    }

    public int[][] getOverlapGridCells_EPSG900913(double longitude1, double latitude1, double longitude2, double latitude2, int width, int height, byte[][] three_state_map, boolean noCellsReturned) {
        int[][] cells = null;
        switch (type) {
            case 0:
                break;
            case 1:
                cells = getOverlapGridCells_Box(longitude1, latitude1, longitude2, latitude2, width, height, bounding_box, three_state_map, noCellsReturned);
                break;
            case 2:
                break; /* TODO: circle grid */
            case 3:
                cells = getOverlapGridCells_Polygon_EPSG900913(longitude1, latitude1, longitude2, latitude2, width, height, three_state_map, noCellsReturned);
        }

        return cells;
    }

    /**
     * stacks PARTIALLY_PRESENT shape outline onto three_state_map
     *
     * @param longitude1
     * @param latitude1
     * @param longitude2
     * @param latitude2
     * @param width
     * @param height
     * @param three_state_map
     * @param noCellsReturned
     */
    public void getOverlapGridCells_Acc_EPSG900913(double longitude1, double latitude1, double longitude2, double latitude2, int width, int height, byte[][] three_state_map) {
        switch (type) {
            case 0:
                break;
            case 1:
                getOverlapGridCells_Box_Acc(longitude1, latitude1, longitude2, latitude2, width, height, bounding_box, three_state_map);
                break;
            case 2:
                break; /* TODO: circle grid */
            case 3:
                getOverlapGridCells_Polygon_Acc_EPSG900913(longitude1, latitude1, longitude2, latitude2, width, height, three_state_map);
        }
    }

    public int[][] getOverlapGridCells_EPSG900913(double longitude1, double latitude1, double longitude2, double latitude2, int width, int height, byte[][] three_state_map) {
        return getOverlapGridCells_EPSG900913(longitude1, latitude1, longitude2, latitude2, width, height, three_state_map, false);
    }

    public int[][] getOverlapGridCells_Polygon_EPSG900913(double olongitude1, double olatitude1, double olongitude2, double olatitude2, int owidth, int oheight, byte[][] three_state_map, boolean noCellsReturned) {
        int i, j;
        if (three_state_map == null) {
            three_state_map = new byte[oheight][owidth];
        }

        SpatialCluster3 sc = new SpatialCluster3();

        int longitude1 = sc.convertLngToPixel(olongitude1);
        int longitude2 = sc.convertLngToPixel(olongitude2);
        int latitude1 = sc.convertLatToPixel(olatitude2);
        int latitude2 = sc.convertLatToPixel(olatitude1);
        int scale = 100; //if it is too small the 'fill' operation is messed up
        int width = owidth * scale;
        int height = oheight * scale;

        double divx = (longitude2 - longitude1) / width;
        double divy = (latitude2 - latitude1) / height;
        double odivx = (olongitude2 - olongitude1) / owidth;
        double odivy = (olatitude2 - olatitude1) / oheight;

        int oy, ox;

        //to cells
        int x, y, xend, yend, xDirection, icross;
        double slope, intercept;
        int xcross, endlat, dx1, dx2, dy1, dy2;
        for (j = 1; j < points.length; j++) {
            if (points[j][1] > points[j - 1][1]) {
                dx1 = sc.convertLngToPixel(points[j][0]);
                dy1 = sc.convertLatToPixel(points[j][1]);
                dx2 = sc.convertLngToPixel(points[j - 1][0]);
                dy2 = sc.convertLatToPixel(points[j - 1][1]);
            } else {
                dx2 = sc.convertLngToPixel(points[j][0]);
                dy2 = sc.convertLatToPixel(points[j][1]);
                dx1 = sc.convertLngToPixel(points[j - 1][0]);
                dy1 = sc.convertLatToPixel(points[j - 1][1]);
            }
            x = (int) ((dx1 - longitude1) / divx);
            y = (int) ((dy1 - latitude1) / divy);
            xend = (int) ((dx2 - longitude1) / divx);
            yend = (int) ((dy2 - latitude1) / divy);

            if (y >= 0 && y < height && x >= 0 && x < width) {
                oy = (int) ((sc.convertPixelToLat((int) (y * divy + latitude1)) - olatitude1) / odivy);
                ox = (int) ((sc.convertPixelToLng((int) (x * divx + longitude1)) - olongitude1) / odivx);
                if (oy >= oheight) {
                    oy = oheight - 1;
                }
                if (ox >= owidth) {
                    ox = owidth - 1;
                }
                if (oy < 0) {
                    oy = 0;
                }
                if (ox < 0) {
                    ox = 0;
                }
                three_state_map[oy][ox] = GI_PARTIALLY_PRESENT;
            }

            if (x == xend && y == yend) {
                continue;
            }

            xDirection = (x < xend) ? 1 : -1;

            slope = (dy1 - dy2) / (double) (dx1 - dx2);
            intercept = (double) (dy1 - slope * dx1);

            if (x == xend) {
                //vertical line
                while (y != yend) {
                    y++;
                    if (y >= 0 && y < height && x >= 0 && x < width) {
                        oy = (int) ((sc.convertPixelToLat((int) (y * divy + latitude1)) - olatitude1) / odivy);
                        ox = (int) ((sc.convertPixelToLng((int) (x * divx + longitude1)) - olongitude1) / odivx);
                        if (oy >= oheight) {
                            oy = oheight - 1;
                        }
                        if (ox >= owidth) {
                            ox = owidth - 1;
                        }
                        if (oy < 0) {
                            oy = 0;
                        }
                        if (ox < 0) {
                            ox = 0;
                        }
                        three_state_map[oy][ox] = GI_PARTIALLY_PRESENT;
                    }
                }
            } else if (y == yend) {
                //horizontal line
                while (x != xend) {
                    x += xDirection;
                    if (y >= 0 && y < height && x >= 0 && x < width) {
                        oy = (int) ((sc.convertPixelToLat((int) (y * divy + latitude1)) - olatitude1) / odivy);
                        ox = (int) ((sc.convertPixelToLng((int) (x * divx + longitude1)) - olongitude1) / odivx);
                        if (oy >= oheight) {
                            oy = oheight - 1;
                        }
                        if (ox >= owidth) {
                            ox = owidth - 1;
                        }
                        if (oy < 0) {
                            oy = 0;
                        }
                        if (ox < 0) {
                            ox = 0;
                        }
                        three_state_map[oy][ox] = GI_PARTIALLY_PRESENT;
                    }
                }
            } else { //sloped line
                endlat = dy2;
                for (int k = (int) ((y + 1) * divy + latitude1); k < endlat; k += divy) {
                    //move in yDirection to get x
                    xcross = (int) ((k - intercept) / slope);
                    icross = (int) ((xcross - longitude1) / divx);

                    while (x != icross && x != xend) {
                        x += xDirection;
                        if (y >= 0 && y < height && x >= 0 && x < width) {
                            oy = (int) ((sc.convertPixelToLat((int) (y * divy + latitude1)) - olatitude1) / odivy);
                            ox = (int) ((sc.convertPixelToLng((int) (x * divx + longitude1)) - olongitude1) / odivx);
                            if (oy >= oheight) {
                                oy = oheight - 1;
                            }
                            if (ox >= owidth) {
                                ox = owidth - 1;
                            }
                            if (oy < 0) {
                                oy = 0;
                            }
                            if (ox < 0) {
                                ox = 0;
                            }
                            three_state_map[oy][ox] = GI_PARTIALLY_PRESENT;
                        }
                    }

                    if (y != yend) {
                        y++;
                        if (y >= 0 && y < height && x >= 0 && x < width) {
                            oy = (int) ((sc.convertPixelToLat((int) (y * divy + latitude1)) - olatitude1) / odivy);
                            ox = (int) ((sc.convertPixelToLng((int) (x * divx + longitude1)) - olongitude1) / odivx);
                            if (oy >= oheight) {
                                oy = oheight - 1;
                            }
                            if (ox >= owidth) {
                                ox = owidth - 1;
                            }
                            if (oy < 0) {
                                oy = 0;
                            }
                            if (ox < 0) {
                                ox = 0;
                            }
                            three_state_map[oy][ox] = GI_PARTIALLY_PRESENT;
                        }
                    }
                }

                //finish horizontal line
                while (x != xend) {
                    x += xDirection;
                    if (y >= 0 && y < height && x >= 0 && x < width) {
                        oy = (int) ((sc.convertPixelToLat((int) (y * divy + latitude1)) - olatitude1) / odivy);
                        ox = (int) ((sc.convertPixelToLng((int) (x * divx + longitude1)) - olongitude1) / odivx);
                        if (oy >= oheight) {
                            oy = oheight - 1;
                        }
                        if (ox >= owidth) {
                            ox = owidth - 1;
                        }
                        if (oy < 0) {
                            oy = 0;
                        }
                        if (ox < 0) {
                            ox = 0;
                        }
                        three_state_map[oy][ox] = GI_PARTIALLY_PRESENT;
                    }
                }
            }
        }

        //do raster check
        int[][] data = new int[owidth * oheight][2];
        boolean cellsReturned = !noCellsReturned;
        int p = 0;
        for (j = 0; j < three_state_map[0].length; j++) {
            for (i = 0; i < three_state_map.length; i++) {
                if (three_state_map[i][j] == GI_PARTIALLY_PRESENT) {
                    //if it is partially present, do nothing
                } else if ((j == 0 || three_state_map[i][j - 1] == GI_PARTIALLY_PRESENT)) {
                    if (i > 0
                            && (three_state_map[i - 1][j] == GI_FULLY_PRESENT
                            || three_state_map[i - 1][j] == GI_ABSENCE)) {
                        //use same as LHS
                        three_state_map[i][j] = three_state_map[i - 1][j];
                    } else if (isWithin_EPSG900913(j * odivx + odivx / 2 + olongitude1, i * odivy + odivy / 2 + olatitude1)) {
                        //if the previous was partially present, test
                        three_state_map[i][j] = GI_FULLY_PRESENT;
                    } //else absent
                } else {
                    //if the previous was fully present, repeat
                    //if the previous was absent, repeat
                    three_state_map[i][j] = three_state_map[i][j - 1];
                }

                //apply to cells;
                if (cellsReturned && three_state_map[i][j] != GI_UNDEFINED) {   //undefined == absence
                    data[p][0] = j;
                    data[p][1] = i;
                    p++;
                }
            }
        }
        return java.util.Arrays.copyOfRange(data, 0, p);
    }

    public void getOverlapGridCells_Polygon_Acc_EPSG900913(double olongitude1, double olatitude1, double olongitude2, double olatitude2, int owidth, int oheight, byte[][] three_state_map) {
        int i, j;
        if (three_state_map == null) {
            three_state_map = new byte[oheight][owidth];
        }

        SpatialCluster3 sc = new SpatialCluster3();

        int longitude1 = sc.convertLngToPixel(olongitude1);
        int longitude2 = sc.convertLngToPixel(olongitude2);
        int latitude1 = sc.convertLatToPixel(olatitude2);
        int latitude2 = sc.convertLatToPixel(olatitude1);
        int scale = 16; //if it is too small the 'fill' operation is messed up
        int width = owidth * scale;
        int height = oheight * scale;

        double divx = (longitude2 - longitude1) / width;
        double divy = (latitude2 - latitude1) / height;
        double odivx = (olongitude2 - olongitude1) / owidth;
        double odivy = (olatitude2 - olatitude1) / oheight;

        int oy, ox;

        //to cells
        int x, y, xend, yend, xDirection, icross;
        double slope, intercept;
        int xcross, endlat, dx1, dx2, dy1, dy2;
        for (j = 1; j < points.length; j++) {
            if (points[j][1] > points[j - 1][1]) {
                dx1 = sc.convertLngToPixel(points[j][0]);
                dy1 = sc.convertLatToPixel(points[j][1]);
                dx2 = sc.convertLngToPixel(points[j - 1][0]);
                dy2 = sc.convertLatToPixel(points[j - 1][1]);
            } else {
                dx2 = sc.convertLngToPixel(points[j][0]);
                dy2 = sc.convertLatToPixel(points[j][1]);
                dx1 = sc.convertLngToPixel(points[j - 1][0]);
                dy1 = sc.convertLatToPixel(points[j - 1][1]);
            }
            x = (int) ((dx1 - longitude1) / divx);
            y = (int) ((dy1 - latitude1) / divy);
            xend = (int) ((dx2 - longitude1) / divx);
            yend = (int) ((dy2 - latitude1) / divy);

            if (y >= 0 && y < height && x >= 0 && x < width) {
                oy = (int) ((sc.convertPixelToLat((int) (y * divy + latitude1)) - olatitude1) / odivy);
                ox = (int) ((sc.convertPixelToLng((int) (x * divx + longitude1)) - olongitude1) / odivx);
                if (oy >= oheight) {
                    oy = oheight - 1;
                }
                if (ox >= owidth) {
                    ox = owidth - 1;
                }
                if (oy < 0) {
                    oy = 0;
                }
                if (ox < 0) {
                    ox = 0;
                }
                three_state_map[oy][ox] = GI_PARTIALLY_PRESENT;
            }

            if (x == xend && y == yend) {
                continue;
            }

            xDirection = (x < xend) ? 1 : -1;

            slope = (dy1 - dy2) / (double) (dx1 - dx2);
            intercept = (double) (dy1 - slope * dx1);

            if (x == xend) {
                //vertical line
                while (y != yend) {
                    y++;
                    if (y >= 0 && y < height && x >= 0 && x < width) {
                        oy = (int) ((sc.convertPixelToLat((int) (y * divy + latitude1)) - olatitude1) / odivy);
                        ox = (int) ((sc.convertPixelToLng((int) (x * divx + longitude1)) - olongitude1) / odivx);
                        if (oy >= oheight) {
                            oy = oheight - 1;
                        }
                        if (ox >= owidth) {
                            ox = owidth - 1;
                        }
                        if (oy < 0) {
                            oy = 0;
                        }
                        if (ox < 0) {
                            ox = 0;
                        }
                        three_state_map[oy][ox] = GI_PARTIALLY_PRESENT;
                    }
                }
            } else if (y == yend) {
                //horizontal line
                while (x != xend) {
                    x += xDirection;
                    if (y >= 0 && y < height && x >= 0 && x < width) {
                        oy = (int) ((sc.convertPixelToLat((int) (y * divy + latitude1)) - olatitude1) / odivy);
                        ox = (int) ((sc.convertPixelToLng((int) (x * divx + longitude1)) - olongitude1) / odivx);
                        if (oy >= oheight) {
                            oy = oheight - 1;
                        }
                        if (ox >= owidth) {
                            ox = owidth - 1;
                        }
                        if (oy < 0) {
                            oy = 0;
                        }
                        if (ox < 0) {
                            ox = 0;
                        }
                        three_state_map[oy][ox] = GI_PARTIALLY_PRESENT;
                    }
                }
            } else { //sloped line
                endlat = dy2;
                for (int k = (int) ((y + 1) * divy + latitude1); k < endlat; k += divy) {
                    //move in yDirection to get x
                    xcross = (int) ((k - intercept) / slope);
                    icross = (int) ((xcross - longitude1) / divx);

                    while (x != icross && x != xend) {
                        x += xDirection;
                        if (y >= 0 && y < height && x >= 0 && x < width) {
                            oy = (int) ((sc.convertPixelToLat((int) (y * divy + latitude1)) - olatitude1) / odivy);
                            ox = (int) ((sc.convertPixelToLng((int) (x * divx + longitude1)) - olongitude1) / odivx);
                            if (oy >= oheight) {
                                oy = oheight - 1;
                            }
                            if (ox >= owidth) {
                                ox = owidth - 1;
                            }
                            if (oy < 0) {
                                oy = 0;
                            }
                            if (ox < 0) {
                                ox = 0;
                            }
                            three_state_map[oy][ox] = GI_PARTIALLY_PRESENT;
                        }
                    }

                    if (y != yend) {
                        y++;
                        if (y >= 0 && y < height && x >= 0 && x < width) {
                            oy = (int) ((sc.convertPixelToLat((int) (y * divy + latitude1)) - olatitude1) / odivy);
                            ox = (int) ((sc.convertPixelToLng((int) (x * divx + longitude1)) - olongitude1) / odivx);
                            if (oy >= oheight) {
                                oy = oheight - 1;
                            }
                            if (ox >= owidth) {
                                ox = owidth - 1;
                            }
                            if (oy < 0) {
                                oy = 0;
                            }
                            if (ox < 0) {
                                ox = 0;
                            }
                            three_state_map[oy][ox] = GI_PARTIALLY_PRESENT;
                        }
                    }
                }

                //finish horizontal line
                while (x != xend) {
                    x += xDirection;
                    if (y >= 0 && y < height && x >= 0 && x < width) {
                        oy = (int) ((sc.convertPixelToLat((int) (y * divy + latitude1)) - olatitude1) / odivy);
                        ox = (int) ((sc.convertPixelToLng((int) (x * divx + longitude1)) - olongitude1) / odivx);
                        if (oy >= oheight) {
                            oy = oheight - 1;
                        }
                        if (ox >= owidth) {
                            ox = owidth - 1;
                        }
                        if (oy < 0) {
                            oy = 0;
                        }
                        if (ox < 0) {
                            ox = 0;
                        }
                        three_state_map[oy][ox] = GI_PARTIALLY_PRESENT;
                    }
                }
            }
        }
    }
}
