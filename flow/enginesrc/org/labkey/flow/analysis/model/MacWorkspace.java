/*
 * Copyright (c) 2006-2011 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.labkey.flow.analysis.model;

import org.apache.commons.lang.StringUtils;
import org.labkey.api.util.Pair;
import org.labkey.flow.analysis.web.GraphSpec;
import org.labkey.flow.analysis.web.StatisticSpec;
import org.labkey.flow.analysis.web.SubsetExpression;
import org.labkey.flow.analysis.web.SubsetSpec;
import org.labkey.flow.persist.AttributeSet;
import org.labkey.flow.persist.ObjectType;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.*;

public class MacWorkspace extends FlowJoWorkspace
{
    static Map<String, StatisticSpec.STAT> statMap = new HashMap<String, StatisticSpec.STAT>();
    static
    {
        statMap.put("Count", StatisticSpec.STAT.Count);
        statMap.put("Percentile", StatisticSpec.STAT.Percentile);
        statMap.put("Mean", StatisticSpec.STAT.Mean);
        statMap.put("Median", StatisticSpec.STAT.Median);
        //statMap.put("Mode", StatisticSpec.STAT.Mode);
        statMap.put("GeometricMean", StatisticSpec.STAT.Geometric_Mean);
        statMap.put("FrequencyOfGrandParent", StatisticSpec.STAT.Freq_Of_Grandparent);
        statMap.put("FrequencyOfParent", StatisticSpec.STAT.Freq_Of_Parent);
        statMap.put("FrequencyOfTotal", StatisticSpec.STAT.Frequency);
        statMap.put("CV", StatisticSpec.STAT.CV);
        statMap.put("SD", StatisticSpec.STAT.Std_Dev);
        statMap.put("MedianAbsDeviation", StatisticSpec.STAT.Median_Abs_Dev);
        statMap.put("MedianAbsDeviation%", StatisticSpec.STAT.Median_Abs_Dev_Percent);
        statMap.put("RobustCV", StatisticSpec.STAT.Robust_CV);
    }


    protected void readSamples(Element elDoc)
    {
        for (Element elSamples : getElementsByTagName(elDoc, "Samples"))
        {
            for (Element elSample : getElementsByTagName(elSamples, "Sample"))
            {
                readSample(elSample);
            }
        }
    }

    protected void readGroups(Element elDoc)
    {
        for (Element elGroups : getElementsByTagName(elDoc, "Groups"))
        {
            for (Element elGroup : getElementsByTagName(elGroups, "Group"))
            {
                readGroup(elGroup);
            }
        }
    }

    public MacWorkspace(String name, Element elDoc) throws Exception
    {
        _name = name;
        readCompensationMatrices(elDoc);
        readAutoCompensationScripts(elDoc);
        readCalibrationTables(elDoc);
        readSamples(elDoc);
        readSampleAnalyses(elDoc);
        readGroups(elDoc);
        readGroupAnalyses(elDoc);
        postProcess();
    }



    protected void readSampleAnalyses(Element elDoc)
    {
        for (Element elSampleAnalyses : getElementsByTagName(elDoc, "SampleAnalyses"))
        {
            for (Element elSampleAnalysis : getElementsByTagName(elSampleAnalyses, "Sample"))
            {
                readSampleAnalysis(elSampleAnalysis);
            }
        }
    }

    public void readCompensationMatrices(Element elDoc)
    {
        for (Element elCompensationMatrices : getElementsByTagName(elDoc, "CompensationMatrices"))
        {
            for (Element elCompensationMatrix : getElementsByTagName(elCompensationMatrices, "CompensationMatrix"))
            {
                _compensationMatrices.add(new CompensationMatrix(elCompensationMatrix));
            }
        }
    }

    public void readAutoCompensationScripts(Element elDoc)
    {
        for (Element elAutoCompScripts : getElementsByTagName(elDoc, "AutoCompensationScripts"))
        {
            for (Element elAutoCompScript : getElementsByTagName(elAutoCompScripts, "Script"))
            {
                AutoCompensationScript script = AutoCompensationScript.readAutoComp(elAutoCompScript);
                if (script != null)
                    _autoCompensationScripts.add(script);
            }
        }
    }

    public void readCalibrationTables(Element elDoc)
    {
        for (Element elCalibrationTables : getElementsByTagName(elDoc, "CalibrationTables"))
        {
            for (Element elCalibrationTable : getElementsByTagName(elCalibrationTables, "Table"))
            {
                _calibrationTables.add(FixedCalibrationTable.fromString(getInnerText(elCalibrationTable)));
            }
        }
    }

    protected Analysis readSampleAnalysis(Element elSampleAnalysis)
    {
        AttributeSet results = new AttributeSet(ObjectType.fcsAnalysis, null);
        Analysis ret = readAnalysis(elSampleAnalysis, results);
        PopulationName name = PopulationName.fromString(elSampleAnalysis.getAttribute("name"));
        ret.setName(name);
        String sampleId = elSampleAnalysis.getAttribute("sampleID");
        _sampleAnalyses.put(sampleId, ret);
        if (results.getStatistics().size() > 0)
        {
            StatisticSpec count = new StatisticSpec(null, StatisticSpec.STAT.Count, null);
            // If the statistic "count" is unavailable, try to get it from the '$TOT" keyword.
            if (!results.getStatistics().containsKey(count))
            {
                SampleInfo sampleInfo = _sampleInfos.get(sampleId);
                if (sampleInfo != null)
                {
                    String strTot = sampleInfo.getKeywords().get("$TOT");
                    if (strTot != null)
                    {
                        results.setStatistic(count, Double.valueOf(strTot).doubleValue());
                    }
                }
            }
            // Fill in the Freq Of Parents that can be determined from the existing stats
            for (Map.Entry<StatisticSpec, Double> entry : new HashMap<StatisticSpec, Double>(results.getStatistics()).entrySet())
            {
                final StatisticSpec spec = entry.getKey();
                if (spec.getStatistic() != StatisticSpec.STAT.Count)
                {
                    continue;
                }
                if (spec.getSubset() == null)
                {
                    continue;
                }
                StatisticSpec freqStat = new StatisticSpec(spec.getSubset(), StatisticSpec.STAT.Freq_Of_Parent, null);
                if (results.getStatistics().containsKey(freqStat))
                {
                    continue;
                }
                Double denominator = results.getStatistics().get(new StatisticSpec(spec.getSubset().getParent(), StatisticSpec.STAT.Count, null));
                if (denominator == null)
                {
                    continue;
                }
                if (entry.getValue().equals(0.0))
                {
                    results.setStatistic(freqStat, 0.0);
                }
                else if (!denominator.equals(0.0))
                {
                    results.setStatistic(freqStat, entry.getValue().doubleValue() / denominator.doubleValue() * 100);
                }
            }
            _sampleAnalysisResults.put(sampleId, results);
        }
        return ret;
    }

    protected SubsetExpression remapExpression(final SubsetExpression expr, final Map<SubsetSpec, SubsetSpec> specs)
    {
        return expr.reduce(new RemapExpressionTransform(specs));
    }

    protected Gate createBooleanGate(final SubsetExpression expr)
    {
        Gate gate = expr.reduce(new GateExpressionTransform());
        // Uck. Store the original expression on the gate so we can find use it later to generate aliases.
        ((SubsetExpressionGate)gate).setOriginalExpression(expr);
        return gate;
    }

    protected Gate readBoolean(SubsetSpec parentSubset, String name, Element elBooleanGate)
    {
        String specification = elBooleanGate.getAttribute("specification");
        Element elGatePaths = getElementsByTagName(elBooleanGate, "GatePaths").get(0);
        Element elStringArray = getElementsByTagName(elGatePaths, "StringArray").get(0);
        List<Element> nlStrings = getElementsByTagName(elStringArray, "String");

        int count = 0;
        Map<SubsetSpec, SubsetSpec> mapping = new HashMap<SubsetSpec, SubsetSpec>();
        for (Element elString : nlStrings)
        {
            String str = getTextValue(elString);

            // Absolute gates don't start with '/'.  Relative gates begin with one or more '/' characters.
            SubsetSpec parent = null;
            if (str.startsWith("/"))
            {
                str = str.substring(1);
                parent = parentSubset;
                while (str.startsWith("/"))
                {
                    str = str.substring(1);
                    if (parent == null)
                        throw new FlowException("Relative population '" + getTextValue(elString) + "' tried to escape from parent subset '" + parentSubset + "'");
                    parent = parent.getParent();
                }
            }

            SubsetSpec subset = SubsetSpec.fromUnescapedString(str);
            if (subset.isExpression())
                throw new FlowException("Population '" + str + "' not allowed in boolean gate expression. Nested boolean expression aren't allowed.");

            // Reroot the relative subset to create an absolute subset path
            if (parent != null)
                subset = parent.createChild(subset);

            SubsetSpec placeholder = new SubsetSpec(null, PopulationName.fromString("G" + count));
            mapping.put(placeholder, subset);
            count++;
        }

//        checkGateCodesInOrder(parentSubset, name, specification);

        specification = specification.replaceAll(" ", "");
        if (!specification.startsWith("(") || !specification.endsWith(")"))
            specification = "(" + specification + ")";
        SubsetExpression expr = SubsetExpression.expression(specification);

        expr = remapExpression(expr, mapping);
        Gate gate = createBooleanGate(expr);
        return gate;
    }

    Set<Pair<String, String>> seenGateSpecfication = new HashSet<Pair<String, String>>();

    // Silly debugging tool to find swapped gates.
    private boolean checkGateCodesInOrder(SubsetSpec parentSubset, String name, String specification)
    {
        String fullGateName = (parentSubset == null ? "" : parentSubset.toString()) + "/" + name;
        if (!seenGateSpecfication.add(new Pair<String, String>(fullGateName, specification)))
            return true;

        StringTokenizer st = new StringTokenizer(specification, "&|", true);
        int gateCount = 0;
        while (st.hasMoreTokens())
        {
            String gateCode = StringUtils.trim(st.nextToken());
            if (gateCode.startsWith("!"))
                gateCode = StringUtils.trim(gateCode.substring(1));

            if (!gateCode.startsWith("G"))
            {
                throw new FlowException(String.format("Gate code does not start with 'G': %s", gateCode));
            }
            else
            {
                // get int from gateCode
                Integer i = Integer.parseInt(gateCode.substring(1));
                if (i != gateCount)
                {
                    System.err.println(String.format("%s\t%s\t%s", _name==null ? "" : _name, fullGateName, specification));
                    return false;
                }
            }

            // skip the operator
            if (st.hasMoreTokens())
                st.nextToken();

            gateCount++;
        }

        return true;
    }
    
    protected void readStats(SubsetSpec subset, Element elPopulation, AttributeSet results, Analysis analysis)
    {
        String strCount = elPopulation.getAttribute("count");
        if (!StringUtils.isEmpty(strCount))
        {
            StatisticSpec statCount = new StatisticSpec(subset, StatisticSpec.STAT.Count, null);
            results.setStatistic(statCount, Double.valueOf(strCount).doubleValue());
        }
        for (Element elStat : getElementsByTagName(elPopulation, "Statistic"))
        {
            String statistic = elStat.getAttribute("statistic");
            StatisticSpec.STAT stat = statMap.get(statistic);
            if (stat == null)
                continue;
            String parameter = StringUtils.trimToNull(elStat.getAttribute("parameter"));
            if (parameter != null)
            {
                parameter = ___cleanName(parameter);
            }
            String percentile = StringUtils.trimToNull(elStat.getAttribute("statisticVariable"));
            StatisticSpec spec;
            if (percentile == null)
            {
                spec = new StatisticSpec(subset, stat, parameter);
            }
            else
            {
                spec = new StatisticSpec(subset, stat, parameter + ":" + percentile);
            }
            String strValue = StringUtils.trimToNull(elStat.getAttribute("value"));
            if (strValue != null)
            {
                double value;
                try
                {
                    value = Double.valueOf(strValue).doubleValue();
                }
                catch (NumberFormatException nfe)
                {
                    continue;
                }
                results.setStatistic(spec, value);
            }
            analysis.addStatistic(spec);
        }
    }

    protected PolygonGate readPolygon(Element el)
    {
        String xAxis = getNormalizedParameterName(el.getAttribute("xAxisName"));
        String yAxis = getNormalizedParameterName(el.getAttribute("yAxisName"));

        List<Double> lstX = new ArrayList<Double>();
        List<Double> lstY = new ArrayList<Double>();
        for (Element elPolygon : getElementsByTagName(el, "Polygon"))
        {
            for (Element elVertex : getElementsByTagName(elPolygon, "Vertex"))
            {
                lstX.add(parseParamValue(xAxis, elVertex, "x"));
                lstY.add(parseParamValue(yAxis, elVertex, "y"));
            }
        }
        scaleValues(xAxis, lstX);
        scaleValues(yAxis, lstY);
        double[] X = toDoubleArray(lstX);
        double[] Y = toDoubleArray(lstY);
        PolygonGate gate = new PolygonGate(xAxis, yAxis, new Polygon(X, Y));
        gate = interpolatePolygon(gate);
        return gate;
    }

    protected Population readPopulation(Element elPopulation, SubsetSpec parentSubset, Analysis analysis, AttributeSet results)
    {
        /*
        SubsetSpec booleanSubset = toBooleanExpression(parentSubset, elPopulation);
        if (booleanSubset != null)
        {
            analysis.addSubset(booleanSubset);
            readStats(booleanSubset, elPopulation, results, analysis);
            return null;
        }
        */

        Population ret = new Population();
        PopulationName name = PopulationName.fromString(elPopulation.getAttribute("name"));
        ret.setName(name);
        SubsetSpec subset = new SubsetSpec(parentSubset, name);
        Set<String> gatedParams = new LinkedHashSet<String>();

        for (Element elBooleanGate : getElementsByTagName(elPopulation, "BooleanGate"))
        {
            Gate gate = readBoolean(parentSubset, name.toString(), elBooleanGate);
            ret.addGate(gate);

            // UNDONE: parse <Graph> element to get x & y axes
            //analysis.addGraph(new GraphSpec(parentSubset, x, y));
        }

        for (Element elPolygonGate : getElementsByTagName(elPopulation, "PolygonGate"))
        {
            NodeList nl = elPolygonGate.getChildNodes();
            for (int iNode = 0; iNode < nl.getLength(); iNode ++)
            {
                Node node = nl.item(iNode);
                if (!(node instanceof Element))
                    continue;
                Element el = (Element) node;
                boolean invert = "1".equals(el.getAttribute("negated")) || "0".equals(el.getAttribute("eventsInside"));
                if ("Polygon".equals(el.getTagName()) || "PolyRect".equals(el.getTagName()))
                {
                    PolygonGate gate = readPolygon(el);
                    ret.addGate(invert ? new NotGate(gate) : gate);
                    analysis.addGraph(new GraphSpec(parentSubset, gate.getXAxis(), gate.getYAxis()));
                }
                else if ("Range".equals(el.getTagName()))
                {
                    String axis = ___cleanName(el.getAttribute("xAxisName"));
                    gatedParams.add(axis);
                    List<Double> lstValues = new ArrayList<Double>();
                    for (Element elPolygon : getElementsByTagName(el, "Polygon"))
                    {
                        for (Element elVertex : getElementsByTagName(elPolygon, "Vertex"))
                        {
                            lstValues.add(parseParamValue(axis, elVertex, "x"));
                        }
                    }
                    scaleValues(axis, lstValues);
                    IntervalGate gate = new IntervalGate(axis, lstValues.get(0).doubleValue(), lstValues.get(1).doubleValue());
                    ret.addGate(invert ? new NotGate(gate) : gate);
                    analysis.addGraph(new GraphSpec(parentSubset, gate.getXAxis()));
                }
                else if ("Ellipse".equals(el.getTagName()))
                {
                    PolygonGate polygon = readPolygon(el);
                    EllipseGate.Point[] vertices = new EllipseGate.Point[4];
                    for (int i = 0; i < vertices.length; i ++)
                    {
                        vertices[i] = new EllipseGate.Point(polygon.getPolygon().X[i], polygon.getPolygon().Y[i]);
                    }
                    EllipseGate gate = EllipseGate.fromVertices(polygon.getXAxis(), polygon.getYAxis(), vertices);
                    ret.addGate(invert ? new NotGate(gate) : gate);
                    analysis.addGraph(new GraphSpec(parentSubset, polygon.getXAxis(), polygon.getYAxis()));
                }
            }
        }

        readStats(subset, elPopulation, results, analysis);

        for (Element elChild: getElementsByTagName(elPopulation, "Population"))
        {
            Population child = readPopulation(elChild, subset, analysis, results);
            if (child != null)
            {
                ret.addPopulation(child);
            }
        }
        return ret;
    }


    protected Analysis readAnalysis(Element elAnalysis, AttributeSet results)
    {
        Analysis ret = new Analysis();
        ret.setSettings(_settings);
        ret.getStatistics().add(new StatisticSpec(null, StatisticSpec.STAT.Count, null));

        readStats(null, elAnalysis, results, ret);

        for (Element elPopulation : getElementsByTagName(elAnalysis, "Population"))
        {
            Population child = readPopulation(elPopulation, null, ret, results);
            if (child != null)
                ret.addPopulation(child);

        }
        if (results != null)
        {
            for (StatisticSpec stat : results.getStatistics().keySet())
            {
                ret.addStatistic(stat);
            }
        }

        return ret;
    }

    protected Analysis readGroupAnalysis(Element elGroupAnalysis)
    {
        Analysis ret = readAnalysis(elGroupAnalysis, null);
        PopulationName name = PopulationName.fromString(elGroupAnalysis.getAttribute("name"));
        ret.setName(name);
        _groupAnalyses.put(ret.getName(), ret);

        // Group name attribute only appears on the GroupAnalysis node in old workspace format.
        String groupID = elGroupAnalysis.getAttribute("groupID");
        GroupInfo groupInfo = _groupInfos.get(groupID);
        if (groupInfo != null && groupInfo.getGroupName() == null)
            groupInfo.setGroupName(ret.getName());

        return ret;
    }

    protected void readGroupAnalyses(Element elDoc)
    {
        for (Element elGroupAnalyses : getElementsByTagName(elDoc, "GroupAnalyses"))
        {
            for (Element elGroupAnalysis : getElementsByTagName(elGroupAnalyses, "Group"))
            {
                readGroupAnalysis(elGroupAnalysis);
            }
        }
    }

    protected void readKeywords(SampleInfo sample, Element el)
    {
        for (Element elKeyword : getElementsByTagName(el, "Keyword"))
        {
            String name = StringUtils.trimToNull(elKeyword.getAttribute("name"));
            if (null == name)
                continue;
            sample._keywords.put(name, elKeyword.getAttribute("value"));
        }
    }

    protected void readParameterInfo(Element el)
    {
        for (Element elParameter : getElementsByTagName(el, "Parameter"))
        {
            String name = getNormalizedParameterName(elParameter.getAttribute("name"));
            if (!_parameters.containsKey(name))
            {
                ParameterInfo pi = new ParameterInfo();
                pi.name = name;
                pi.multiplier = findMultiplier(elParameter);
                String calibrationIndex = elParameter.getAttribute("calibrationIndex");
                if (!StringUtils.isEmpty(calibrationIndex))
                {
                    int index = Integer.valueOf(calibrationIndex).intValue();
                    if (index > 0 && index <= _calibrationTables.size())
                    {
                        pi.calibrationTable = _calibrationTables.get(index - 1);
                    }
                }

                if (pi.calibrationTable == null)
                {
                    pi.calibrationTable = new IdentityCalibrationTable(getRange(elParameter));
                }
                _parameters.put(name, pi);
            }
            String lowValue = elParameter.getAttribute("lowValue");
            if (lowValue != null)
            {
                _settings.getParameterInfo(name, true).setMinValue(Double.valueOf(lowValue).doubleValue());
            }
        }
    }

    protected SampleInfo readSample(Element elSample)
    {
        SampleInfo ret = new SampleInfo();
        ret._sampleId = elSample.getAttribute("sampleID");
        if (elSample.hasAttribute("compensationID"))
        {
            ret._compensationId = elSample.getAttribute("compensationID");
        }
        for (Element elFCSHeader : getElementsByTagName(elSample, "FCSHeader"))
        {
            readKeywords(ret, elFCSHeader);
        }
        readParameterInfo(elSample);
        _sampleInfos.put(ret._sampleId, ret);
        return ret;
    }

    protected GroupInfo readGroup(Element elGroup)
    {
        GroupInfo ret = new GroupInfo();
        ret._groupId = elGroup.getAttribute("id");
        for (Element elSampleList : getElementsByTagName(elGroup, "SampleList"))
        {
            for (Element elSample : getElementsByTagName(elSampleList, "SampleID"))
            {
                String sampleID = StringUtils.trimToNull(elSample.getTextContent());
                if (sampleID != null)
                    ret._sampleIds.add(sampleID);
            }
        }

        _groupInfos.put(ret._groupId, ret);
        return ret;
    }
}
