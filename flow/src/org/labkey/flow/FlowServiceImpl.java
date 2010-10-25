/*
 * Copyright (c) 2006-2010 LabKey Corporation
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

package org.labkey.flow;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Table;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.flow.api.FlowService;
import org.labkey.flow.persist.FlowManager;

import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;


/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Oct 25, 2010
 * Time: 11:16:08 AM
 */

public class FlowServiceImpl implements FlowService
{
    @Override
    public List<ExpData> getExpDataByURL(String canonicalURL, @Nullable Container container)
    {
        List<ExpData> ret = new LinkedList<ExpData>();
        SQLFragment sql = new SQLFragment("SELECT dataid FROM " + FlowManager.get().getTinfoObject().getFromSQL("O") + " WHERE uri=?");
        sql.add(canonicalURL);
        if (null != container)
        {
            sql.append(" AND container=?");
            sql.add(container);
        }
        try
        {
            Integer[] dataids = Table.executeArray(FlowManager.get().getSchema(), sql, Integer.class);
            for (Integer dataid : dataids)
            {
                ExpData data = ExperimentService.get().getExpData(dataid);
                if (null != data)
                    ret.add(data);
            }
            return ret;
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }
}
