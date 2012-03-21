package org.labkey.nab;

import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.assay.dilution.DilutionCurve;
import org.labkey.api.study.Plate;
import org.labkey.api.study.Position;
import org.labkey.api.study.Well;
import org.labkey.api.study.WellGroup;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: brittp
 * Date: Mar 12, 2010 9:43:44 AM
 */
public class NabRunPropertyMap extends HashMap<String, Object>
{
    private static class PropertyNameMap extends HashMap<String, Object>
    {
        public PropertyNameMap(Map<PropertyDescriptor, Object> properties)
        {
            for (Map.Entry<PropertyDescriptor, Object> entry : properties.entrySet())
                put(entry.getKey().getName(), entry.getValue());
        }
    }

    public NabRunPropertyMap(NabAssayRun assay, boolean includeStats, boolean includeWells, boolean calculateNeut, boolean includeFitParameters)
    {
        put("runId", assay.getRun().getRowId());
        put("properties", new PropertyNameMap(assay.getRunProperties()));
        put("containerPath", assay.getRun().getContainer().getPath());
        put("containerId", assay.getRun().getContainer().getId());
        put("cutoffs", assay.getCutoffs());
        List<Map<String, Object>> samples = new ArrayList<Map<String, Object>>();
        for (NabAssayRun.SampleResult result : assay.getSampleResults())
        {
            Map<String, Object> sample = new HashMap<String, Object>();
            sample.put("properties", new PropertyNameMap(result.getSampleProperties()));
            DilutionSummary dilutionSummary = result.getDilutionSummary();
            sample.put("objectId", result.getObjectId());
            sample.put("wellgroupName", dilutionSummary.getFirstWellGroup().getName());
            try
            {
                if (includeStats)
                {
                    sample.put("minDilution", dilutionSummary.getMinDilution(assay.getRenderedCurveFitType()));
                    sample.put("maxDilution", dilutionSummary.getMaxDilution(assay.getRenderedCurveFitType()));
                }
                if (calculateNeut)
                {
                    sample.put("fitError", dilutionSummary.getFitError());
                    for (int cutoff : assay.getCutoffs())
                    {
                        sample.put("curveIC" + cutoff, dilutionSummary.getCutoffDilution(cutoff/100.0, assay.getRenderedCurveFitType()));
                        sample.put("pointIC" + cutoff, dilutionSummary.getInterpolatedCutoffDilution(cutoff/100.0, assay.getRenderedCurveFitType()));
                    }
                }
                if (includeFitParameters)
                {
                    sample.put("fitParameters", dilutionSummary.getCurveParameters(assay.getRenderedCurveFitType()).toMap());
                }
                List<Map<String, Object>> replicates = new ArrayList<Map<String, Object>>();
                for (WellGroup sampleGroup : dilutionSummary.getWellGroups())
                {
                    for (WellGroup replicate : sampleGroup.getOverlappingGroups(WellGroup.Type.REPLICATE))
                    {
                        Map<String, Object> replicateProps = new HashMap<String, Object>();
                        replicateProps.put("dilution", replicate.getDilution());
                        if (calculateNeut)
                        {
                            replicateProps.put("neutPercent", dilutionSummary.getPercent(replicate));
                            replicateProps.put("neutPlusMinus", dilutionSummary.getPlusMinus(replicate));
                        }
                        addStandardWellProperties(replicate, replicateProps, includeStats, includeWells);
                        replicates.add(replicateProps);
                    }
                }
                sample.put("replicates", replicates);
            }
            catch (DilutionCurve.FitFailedException e)
            {
                throw new RuntimeException(e);
            }

            samples.add(sample);
        }
        put("samples", samples);

        List<Plate> plates = assay.getPlates();
        if (plates != null)
        {
            for (int i = 0; i < plates.size(); i++)
            {
                String indexSuffix = plates.size() > 1 ? "" + (i + 1) : "";
                Plate plate = plates.get(i);
                WellGroup cellControl = plate.getWellGroup(WellGroup.Type.CONTROL, NabManager.CELL_CONTROL_SAMPLE);
                Map<String, Object> cellControlProperties = new HashMap<String, Object>();
                addStandardWellProperties(cellControl, cellControlProperties, includeStats, includeWells);
                put("cellControl" + indexSuffix, cellControlProperties);

                WellGroup virusControl = plate.getWellGroup(WellGroup.Type.CONTROL, NabManager.VIRUS_CONTROL_SAMPLE);
                Map<String, Object> virusControlProperties = new HashMap<String, Object>();
                addStandardWellProperties(virusControl, virusControlProperties, includeStats, includeWells);
                put("virusControl" + indexSuffix, virusControlProperties);
            }
        }
    }

    private void addStandardWellProperties(WellGroup group, Map<String, Object> properties, boolean includeStats, boolean includeWells)
    {
        if (includeStats)
        {
            properties.put("min", group.getMin());
            properties.put("max", group.getMax());
            properties.put("mean", group.getMean());
            properties.put("stddev", group.getStdDev());
        }
        if (includeWells)
        {
            List<Map<String, Object>> wellList = new ArrayList<Map<String, Object>>();
            for (Position position : group.getPositions())
            {
                Map<String, Object> wellProps = new HashMap<String, Object>();
                Well well = group.getPlate().getWell(position.getRow(), position.getColumn());
                wellProps.put("row", well.getRow());
                wellProps.put("column", well.getColumn());
                wellProps.put("value", well.getValue());
                wellList.add(wellProps);
            }
            properties.put("wells", wellList);
        }
    }
}
