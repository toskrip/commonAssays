/*
 * Copyright (c) 2013 LabKey Corporation
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
package org.labkey.nab.multiplate;

import org.labkey.api.assay.dilution.DilutionCurve;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.OntologyObject;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.reader.ColumnDescriptor;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.reader.ExcelLoader;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.security.User;
import org.labkey.api.study.Plate;
import org.labkey.api.study.PlateService;
import org.labkey.api.study.PlateTemplate;
import org.labkey.api.study.WellGroup;
import org.labkey.api.study.assay.AssayDataType;
import org.labkey.api.util.FileType;
import org.labkey.api.util.Pair;
import org.labkey.nab.DilutionSummary;
import org.labkey.nab.NabAssayProvider;
import org.labkey.nab.NabAssayRun;
import org.labkey.nab.NabManager;
import org.labkey.nab.NabSpecimen;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: 2/24/13
 */
public class SinglePlateDilutionNabDataHandler extends HighThroughputNabDataHandler
{
    public static final AssayDataType SINGLE_PLATE_DILUTION_DATA_TYPE = new AssayDataType("SinglePlateDilutionAssayRunNabData", new FileType(".csv"));

    public Priority getPriority(ExpData data)
    {
        Lsid lsid = new Lsid(data.getLSID());
        if (SINGLE_PLATE_DILUTION_DATA_TYPE.matches(lsid))
        {
            return Priority.HIGH;
        }
        return null;
    }

    @Override
    public DataType getDataType()
    {
        return SINGLE_PLATE_DILUTION_DATA_TYPE;
    }

    @Override
    protected List<Plate> createPlates(File dataFile, PlateTemplate template) throws ExperimentException
    {
        DataLoader loader;
        try
        {
            if (dataFile.getName().toLowerCase().endsWith(".csv"))
            {
                loader = new TabLoader(dataFile, true);
                ((TabLoader) loader).parseAsCSV();
            }
            else
                loader = new ExcelLoader(dataFile, true);

            int wellsPerPlate = template.getRows() * template.getColumns();

            ColumnDescriptor[] columns = loader.getColumns();
            if (columns == null || columns.length == 0)
            {
                throwParseError(dataFile, "No columns found in data file.");
                // return to suppress intellij warnings (line above will always throw):
                return null;
            }

            // The results column is defined as the last column in the file for this file format:
            String resultColumnHeader = columns[columns.length - 1].name;
            String virusNameColumnHeader = NabAssayProvider.VIRUS_NAME_PROPERTY_NAME;

            int wellCount = 0;
            int plateCount = 0;
            double[][] wellValues = new double[template.getRows()][template.getColumns()];
            List<Plate> plates = new ArrayList<Plate>();
            Map<Integer, String> plateToVirusMap = new HashMap<Integer, String>();

            for (Map<String, Object> rowData : loader)
            {
                // Current line in the data file is calculated by the number of wells we've already read,
                // plus one for the current row, plus one for the header row:
                int line = plateCount * wellsPerPlate + wellCount + 2;
                Pair<Integer, Integer> location = getWellLocation(template, dataFile, rowData, line);
                int plateRow = location.getKey();
                int plateCol = location.getValue();

                Object dataValue = rowData.get(resultColumnHeader);
                if (dataValue == null || !(dataValue instanceof Integer))
                {
                    throwParseError(dataFile, "No valid result value found on line " + line + ".  Expected integer " +
                            "result values in the last data file column (\"" + resultColumnHeader + "\") found: " + dataValue);
                    return null;
                }

                Object virusName = rowData.get(virusNameColumnHeader);
                if (virusName == null || !(virusName instanceof String))
                {
                    throwParseError(dataFile, "No valid virus name value found on line " + line + ".  Expected string " +
                            "result values  (\"" + virusNameColumnHeader + "\") found: " + virusName);
                    return null;
                }

                if (!plateToVirusMap.containsKey(plateCount))
                    plateToVirusMap.put(plateCount, virusName.toString());
                else
                {
                    if (!plateToVirusMap.get(plateCount).equals(virusName.toString()))
                        throwParseError(dataFile, "more than one virus name on a plate found for : plate " + plateCount +
                                " and virus name : " + virusName);
                }

                wellValues[plateRow - 1][plateCol - 1] = (Integer) dataValue;
                if (++wellCount == wellsPerPlate)
                {
                    Plate plate = PlateService.get().createPlate(template, wellValues);
                    plate.setProperty(NabAssayProvider.VIRUS_NAME_PROPERTY_NAME, plateToVirusMap.get(plateCount));

                    plates.add(plate);
                    plateCount++;
                    wellCount = 0;
                }
            }
            if (wellCount != 0)
            {
                throwParseError(dataFile, "Expected well data in multiples of " + wellsPerPlate + ".  The file provided included " +
                        plateCount + " complete plates of data, plus " + wellCount + " extra rows.");
            }
            return plates;
        }
        catch (IOException e)
        {
            throwParseError(dataFile, null, e);
            return null;
        }
    }

    @Override
    protected Map<ExpMaterial, List<WellGroup>> getMaterialWellGroupMapping(NabAssayProvider provider, List<Plate> plates, Collection<ExpMaterial> sampleInputs) throws ExperimentException
    {
        Map<String, ExpMaterial> nameToMaterial = new HashMap<String, ExpMaterial>();
        for (ExpMaterial material : sampleInputs)
            nameToMaterial.put(material.getName(), material);

        Map<ExpMaterial, List<WellGroup>> mapping = new HashMap<ExpMaterial, List<WellGroup>>();
        for (Plate plate : plates)
        {
            List<? extends WellGroup> specimenGroups = plate.getWellGroups(WellGroup.Type.SPECIMEN);
            for (WellGroup specimenGroup : specimenGroups)
            {
                String name = specimenGroup.getName();
                String virusName = plate.getProperty(NabAssayProvider.VIRUS_NAME_PROPERTY_NAME).toString();
                String key = SinglePlateDilutionSamplePropertyHelper.getKey(virusName, name);

                ExpMaterial material = nameToMaterial.get(key);
                if (material == null)
                {
                    throw new ExperimentException("Unable to find sample metadata for sample well group \"" + name +
                            "\": your sample metadata file may contain incorrect well group names, or it may not list all required samples.");
                }
                List<WellGroup> materialWellGroups = mapping.get(material);
                if (materialWellGroups == null)
                {
                    materialWellGroups = new ArrayList<WellGroup>();
                    mapping.put(material, materialWellGroups);
                }
                materialWellGroups.add(specimenGroup);
            }
        }
        return mapping;
    }

    @Override
    protected NabAssayRun createNabAssayRun(NabAssayProvider provider, ExpRun run, List<Plate> plates, User user, List<Integer> sortedCutoffs, DilutionCurve.FitType fit)
    {
        return new SinglePlateDilutionNabAssayRun(provider, run, plates, user, sortedCutoffs, fit);
    }

    public Lsid getDataRowLSID(ExpData data, String wellGroupName, Map<PropertyDescriptor, Object> sampleProperties)
    {
        String virusName = "";
        for (Map.Entry<PropertyDescriptor, Object> entry : sampleProperties.entrySet())
        {
            if (entry.getKey().getName().equals(NabAssayProvider.VIRUS_NAME_PROPERTY_NAME))
                virusName = entry.getValue().toString();
        }
        Lsid dataRowLsid = new Lsid(data.getLSID());
        dataRowLsid.setNamespacePrefix(NAB_DATA_ROW_LSID_PREFIX);
        dataRowLsid.setObjectId(dataRowLsid.getObjectId() + "-" + virusName + "-" + wellGroupName);
        return dataRowLsid;
    }

    @Override
    public Map<DilutionSummary, NabAssayRun> getDilutionSummaries(User user, DilutionCurve.FitType fit, int... dataObjectIds) throws ExperimentException, SQLException
    {
        Map<DilutionSummary, NabAssayRun> summaries = new LinkedHashMap<DilutionSummary, NabAssayRun>();
        if (dataObjectIds == null || dataObjectIds.length == 0)
            return summaries;

        Map<String, NabAssayRun> dataToAssay = new HashMap<String, NabAssayRun>();
        for (int dataObjectId : dataObjectIds)
        {
            OntologyObject dataRow = OntologyManager.getOntologyObject(dataObjectId);
            if (dataRow == null || dataRow.getOwnerObjectId() == null)
                continue;
            Map<String, ObjectProperty> properties = OntologyManager.getPropertyObjects(dataRow.getContainer(), dataRow.getObjectURI());
            String wellgroupName = null;
            String specimenLsid = null;
            String virusName = null;

            // samples are keyed by both sampleID and virusName
            for (ObjectProperty property : properties.values())
            {
                if (WELLGROUP_NAME_PROPERTY.equals(property.getName()))
                    wellgroupName = property.getStringValue();
                if (NAB_INPUT_MATERIAL_DATA_PROPERTY.equals(property.getName()))
                    specimenLsid = property.getStringValue();

                if (wellgroupName != null && specimenLsid != null)
                    break;
            }
            if (wellgroupName == null || specimenLsid == null)
                continue;

            // get the virus name from the material input
            ExpMaterial inputMaterial = ExperimentService.get().getExpMaterial(specimenLsid);
            if (inputMaterial != null)
            {
                for (Map.Entry<PropertyDescriptor, Object> entry : inputMaterial.getPropertyValues().entrySet())
                {
                    if (NabAssayProvider.VIRUS_NAME_PROPERTY_NAME.equals(entry.getKey().getName()))
                    {
                        virusName = entry.getValue().toString();
                        break;
                    }
                }
            }

            if (virusName == null)
                continue;

            OntologyObject dataParent = OntologyManager.getOntologyObject(dataRow.getOwnerObjectId());
            if (dataParent == null)
                continue;
            String dataLsid = dataParent.getObjectURI();
            NabAssayRun assay = dataToAssay.get(dataLsid);
            if (assay == null)
            {
                ExpData dataObject = ExperimentService.get().getExpData(dataLsid);
                if (dataObject == null)
                    continue;
                assay = getAssayResults(dataObject.getRun(), user, fit);
                if (assay == null)
                    continue;
                dataToAssay.put(dataLsid, assay);
            }

            for (DilutionSummary summary : assay.getSummaries())
            {
                WellGroup group = summary.getFirstWellGroup();
                Object virusProp = group.getProperty(NabAssayProvider.VIRUS_NAME_PROPERTY_NAME);

                if (wellgroupName.equals(group.getName()) && (virusProp != null && virusProp.toString().equals(virusName)))
                {
                    summaries.put(summary, assay);
                }
            }
        }
        return summaries;
    }
}