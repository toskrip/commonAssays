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
 * Features filter for retention time values
 * Created by IntelliJ IDEA.
 * User: Dave
 * Date: Jan 16, 2008
 * Time: 2:43:48 PM
 */
public class RetentionTimeFilter implements FeaturesFilter
{
    private double _rtLow = 0;
    private double _rtHigh = 0;

    public RetentionTimeFilter(Feature source, double offset)
    {
        _rtLow = source.getTime().doubleValue() - offset;
        _rtHigh = source.getTime().doubleValue() + offset;
    }

    public RetentionTimeFilter(double rtLow, double rtHigh)
    {
        assert rtLow <= rtHigh : "rtLow > rtHigh!";
        _rtLow = rtLow;
        _rtHigh = rtHigh;
    }

    public void setFilters(FeaturesTableInfo tinfo)
    {
        tinfo.addCondition(new SQLFragment("Time BETWEEN " + _rtLow + " AND " + _rtHigh), "Time");

    }
}
