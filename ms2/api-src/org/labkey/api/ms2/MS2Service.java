/*
 * Copyright (c) 2015-2019 LabKey Corporation
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

package org.labkey.api.ms2;

import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;

/**
 * User: jeckels
 * Date: Jan 9, 2007
 */
public interface MS2Service
{
    static void setInstance(MS2Service serviceImpl)
    {
        ServiceRegistry.get().registerService(MS2Service.class, serviceImpl);
    }

    static MS2Service get()
    {
        return ServiceRegistry.get().getService(MS2Service.class);
    }

    SearchClient createSearchClient(String server, String url, Logger instanceLogger, String userAccount, String userPassword);

    UserSchema createSchema(User user, Container container);

    TableInfo createPeptidesTableInfo(User user, Container container);

    TableInfo createSequencesTableInfo(User user, Container container);

    TableInfo createPeptidesTableInfo(User user, Container container, boolean includeFeatureFk,
                                      ContainerFilter containerFilter, SimpleFilter filter, Iterable<FieldKey> defaultColumns);
}
