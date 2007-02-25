package org.labkey.flow.analysis.chart;

import org.jfree.data.xy.AbstractIntervalXYDataset;
import org.labkey.flow.analysis.data.NumberArray;

import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Map;

public class HistDataset extends AbstractIntervalXYDataset
{
    double[] _xValues;
    int[] _yValues;

    public HistDataset(double[] bins, NumberArray numbers)
    {
        _xValues = bins;
        _yValues = new int[bins.length];
        for (int i = 0; i < numbers.size(); i ++)
        {
            double value = numbers.getDouble(i);
            int bucket = DensityDataset.findBucket(bins, value);
            _yValues[bucket] ++;
        }
    }

    public int getSeriesCount()
    {
        return 1;
    }

    public Comparable getSeriesKey(int series)
    {
        return "Series";
    }

    public int getItemCount(int series)
    {
        return _xValues.length;
    }

    public Double getX(int series, int item)
    {
        return _xValues[item];
    }

    public Integer getY(int series, int item)
    {
        return _yValues[item];
    }

    public Double getStartX(int series, int item)
    {
        if (item > 0)
        {
            return (_xValues[item] + _xValues[item - 1]) / 2;
        }
        return _xValues[0];
    }

    public Double getEndX(int series, int item)
    {
        if (item < _xValues.length - 1)
        {
            return (_xValues[item] + _xValues[item + 1]) / 2;
        }
        return _xValues[item];
    }

    public Number getEndY(int series, int item)
    {
        return getY(series, item) + 1;
    }

    public Number getStartY(int series, int item)
    {
        return getY(series, item) - 1;
    }
}
