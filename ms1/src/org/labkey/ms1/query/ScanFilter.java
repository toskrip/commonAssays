/*
 * Copyright (c) 2007 LabKey Software Foundation
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
package org.labkey.ms1.query;

import org.labkey.api.data.SQLFragment;
import org.labkey.ms1.model.Feature;

/**
 * Features filter for scan numbers
 * Created by IntelliJ IDEA.
 * User: Dave
 * Date: Jan 16, 2008
 * Time: 2:44:13 PM
 */
public class ScanFilter implements FeaturesFilter
{
    private int _scanLow = 0;
    private int _scanHigh = 0;

    public ScanFilter(Feature source, int offset)
    {
        _scanLow = source.getScan().intValue() - offset;
        _scanHigh = source.getScan().intValue() + offset;
    }

    public ScanFilter(int scanLow, int scanHigh)
    {
        assert scanLow <= scanHigh : "scanLow value is > scanHigh value!";
        _scanLow = scanLow;
        _scanHigh = scanHigh;
    }

    public void setFilters(FeaturesTableInfo tinfo)
    {
        tinfo.addCondition(new SQLFragment("Scan BETWEEN " + _scanLow + " AND " + _scanHigh), "Scan");
    }
}
