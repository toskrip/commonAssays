package org.labkey.nab;

import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.reader.ColumnDescriptor;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.reader.ExcelLoader;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.security.User;
import org.labkey.api.study.DilutionCurve;
import org.labkey.api.study.Plate;
import org.labkey.api.study.PlateService;
import org.labkey.api.study.PlateTemplate;
import org.labkey.api.study.WellData;
import org.labkey.api.study.WellGroup;
import org.labkey.api.study.assay.AssayDataType;
import org.labkey.api.util.FileType;
import org.labkey.api.util.Pair;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Copyright (c) 2010-2011 LabKey Corporation
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * <p/>
 * <p/>
 * User: brittp
 * Date: Aug 27, 2010 11:07:33 AM
 */
public class HighThroughputNabDataHandler extends NabDataHandler
{
    public static final AssayDataType NAB_HIGH_THROUGHPUT_DATA_TYPE = new AssayDataType("HighThroughputAssayRunNabData", new FileType(".csv"));

    public Priority getPriority(ExpData data)
    {
        Lsid lsid = new Lsid(data.getLSID());
        if (NAB_HIGH_THROUGHPUT_DATA_TYPE.matches(lsid))
        {
            return Priority.HIGH;
        }
        return null;
    }

    @Override
    protected String getPreferredDataFileExtension()
    {
        return "csv";
    }

    @Override
    protected DataType getDataType()
    {
        return NAB_HIGH_THROUGHPUT_DATA_TYPE;
    }

    private static final String LOCATION_COLUMNN_HEADER = "Well Location";

    private void throwWellLocationParseError(File dataFile, int lineNumber, Object locationValue) throws ExperimentException
    {
        throwParseError(dataFile, "Failed to find valid location in column \"" + LOCATION_COLUMNN_HEADER + "\" on line " + lineNumber +
                    ".  Locations should be identified by a single row letter and column number, such as " +
                    "A1 or P24.  Found \"" + (locationValue != null ? locationValue.toString() : "") + "\".");
    }

    private Pair<Integer, Integer> getWellLocation(PlateTemplate template, File dataFile, Map<String, Object> line, int lineNumber) throws ExperimentException
    {
        Object locationValue = line.get(LOCATION_COLUMNN_HEADER);
        if (locationValue == null || !(locationValue instanceof String) || ((String) locationValue).length() < 2)
            throwWellLocationParseError(dataFile, lineNumber, locationValue);
        String location = (String) locationValue;
        Character rowChar = location.charAt(0);
        rowChar = Character.toUpperCase(rowChar);
        if (!(rowChar >= 'A' && rowChar <= 'Z'))
            throwWellLocationParseError(dataFile, lineNumber, locationValue);

        Integer col;
        try
        {
            col = Integer.parseInt(location.substring(1));
        }
        catch (NumberFormatException e)
        {
            throwWellLocationParseError(dataFile, lineNumber, locationValue);
            // return to suppress intellij warnings (line will never be reached)
            return null;
        }
        int row = rowChar - 'A' + 1;

        // 1-based row and column indexing:
        if (row > template.getRows())
        {
            throwParseError(dataFile, "Invalid row " + row + " specified on line " + lineNumber +
                    ".  The current plate template defines " + template.getRows() + " rows.");
        }

        // 1-based row and column indexing:
        if (col > template.getColumns())
        {
            throwParseError(dataFile, "Invalid column " + col + " specified on line " + lineNumber +
                    ".  The current plate template defines " + template.getColumns() + " columns.");
        }
        return new Pair<Integer, Integer>(row, col);
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

            int wellCount = 0;
            int plateCount = 0;
            double[][] wellValues = new double[template.getRows()][template.getColumns()];
            List<Plate> plates = new ArrayList<Plate>();
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

                wellValues[plateRow - 1][plateCol - 1] = (Integer) dataValue;
                if (++wellCount == wellsPerPlate)
                {
                    plates.add(PlateService.get().createPlate(template, wellValues));
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
    protected boolean isDilutionDownOrRight()
    {
        return true;
    }

    @Override
    protected void prepareWellGroups(List<WellGroup> groups, ExpMaterial sampleInput, Map<String, DomainProperty> properties)
    {
        List<WellData> wells = new ArrayList<WellData>();
        // All well groups use the same plate template, so it's okay to just check the dilution direction of the first group:
        boolean reverseDirection = Boolean.parseBoolean((String) groups.get(0).getProperty(NabManager.SampleProperty.ReverseDilutionDirection.name()));
        for (WellGroup group : groups)
        {
            for (DomainProperty property : properties.values())
                group.setProperty(property.getName(), sampleInput.getProperty(property));
            wells.addAll(group.getWellData(true));
        }
        applyDilution(wells, sampleInput, properties, reverseDirection);
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
                ExpMaterial material = nameToMaterial.get(name);
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
        return new HighThroughputNabAssayRun(provider, run, plates, user, sortedCutoffs, fit);
    }
}
