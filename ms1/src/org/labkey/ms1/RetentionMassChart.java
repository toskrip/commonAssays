package org.labkey.ms1;

import org.labkey.api.data.Table;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.axis.LogarithmicAxis;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.contour.DefaultContourDataset;

import java.sql.SQLException;
import java.util.ArrayList;

/**
 * Produces the retention time vs. m/z chart for the feature details view
 * Created by IntelliJ IDEA.
 * User: Dave
 * Date: Oct 15, 2007
 * Time: 12:44:30 PM
 */
public class RetentionMassChart extends FeatureChart
{
    public RetentionMassChart(int runId, double mzLow, double mzHigh, int scanFirst, int scanLast)
    {
        _runId = runId;
        _mzLow = mzLow;
        _mzHigh = mzHigh;
        _scanFirst = scanFirst;
        _scanLast = scanLast;
    }


    protected Table.TableResultSet getChartData() throws SQLException
    {
        return MS1Manager.get().getPeakData(_runId, _mzLow, _mzHigh, _scanFirst, _scanLast);
    }

    protected JFreeChart makeChart(Table.TableResultSet rs) throws SQLException
    {
        ArrayList<Double> xvals = new ArrayList<Double>();
        ArrayList<Double> yvals = new ArrayList<Double>();
        ArrayList<Double> zvals = new ArrayList<Double>();

        //variables to hold min/max values from Y and Z
        //start out with max for min and min for max so
        //the first values will become max and min
        double minY = Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxY = Double.MIN_VALUE;
        double maxZ = Double.MIN_VALUE;
        double curZ;
        LogarithmicAxis logaxis = new LogarithmicAxis("temp");

        while(rs.next())
        {
            xvals.add(rs.getDouble("MZ"));
            yvals.add(rs.getDouble("RetentionTime"));
            curZ = logaxis.adjustedLog10(rs.getDouble("Intensity"));
            zvals.add(curZ);

            minY = Math.min(minY, rs.getDouble("RetentionTime"));
            minZ = Math.min(minZ, curZ);
            maxY = Math.max(maxY, rs.getDouble("RetentionTime"));
            maxZ = Math.max(maxZ, curZ);
        }

        //scale the Z values so that they will be a reasonable size
        //comapred to the Y axis, which is what the chart uses to
        //calculate the bubble diameter
        for(int idx = 0; idx < zvals.size(); ++idx)
            zvals.set(idx, (zvals.get(idx).doubleValue() / maxZ) * (maxY - minY) * .05);

        //NOTE: in version 1.0.2 of JFreeChart, the DefaultContourDataset was replaced with
        //the DefaultXYZDataset class. The former is marked as depricated in versions 1.0.4 and beyond.
        //When we upgrade our JFreeChart package to version 1.0.2 or later, change this to use
        //DefaultXYZDataset.
        DefaultContourDataset dataset = new DefaultContourDataset("Intensities", xvals.toArray(), yvals.toArray(), zvals.toArray());

        return ChartFactory.createBubbleChart("Intensities for Scans " + _scanFirst + " through " + _scanLast,
                                                            "m/z", "Retention Time", dataset, PlotOrientation.VERTICAL,
                                                            false, false, false);

    }

    private int _runId = -1;
    private double _mzLow = 0;
    private double _mzHigh = 0;
    private int _scanFirst = 0;
    private int _scanLast = 0;
}
