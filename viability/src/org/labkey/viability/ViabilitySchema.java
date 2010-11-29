/*
 * Copyright (c) 2009 LabKey Corporation
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

package org.labkey.viability;

import org.labkey.api.data.DbSchema;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.data.TableInfo;

/**
 * User: kevink
 * Date: Sep 17, 2009
 */
public class ViabilitySchema
{
    public enum Tables
    {
        Results, ResultSpecimens
    }

    public static final String SCHEMA_NAME = "viability";
    private static ViabilitySchema _instance = null;

    public static ViabilitySchema get()
    {
        if (null == _instance)
            _instance = new ViabilitySchema();
        return _instance;
    }

    private ViabilitySchema() { }

    public static DbSchema getSchema()
    {
        return DbSchema.get(SCHEMA_NAME);
    }

    public String getSchemaName()
    {
        return SCHEMA_NAME;
    }

    public SqlDialect getSqlDialect()
    {
        return getSchema().getSqlDialect();
    }

    public static TableInfo getTableInfoResults()
    {
        return getSchema().getTable(Tables.Results.name());
    }

    public static TableInfo getTableInfoResultSpecimens()
    {
        return getSchema().getTable(Tables.ResultSpecimens.name());
    }
}
