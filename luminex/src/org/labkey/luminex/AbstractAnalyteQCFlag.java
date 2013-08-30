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
package org.labkey.luminex;

import org.labkey.api.exp.ExpQCFlag;

public abstract class AbstractAnalyteQCFlag extends ExpQCFlag
{
    private int _analyte;

    public AbstractAnalyteQCFlag() {}

    public AbstractAnalyteQCFlag(int runId, String flagType, String description, int analyte)
    {
        super(runId, flagType, description);
        setAnalyte(analyte);
        setEnabled(true);
    }

    public int getAnalyte()
    {
        return _analyte;
    }

    public void setAnalyte(int analyte)
    {
        _analyte = analyte;
        setIntKey1(analyte);
    }

    public void setRun(int run)
    {
        setRunId(run);
    }
}