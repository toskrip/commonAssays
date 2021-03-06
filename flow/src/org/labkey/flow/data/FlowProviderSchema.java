/*
 * Copyright (c) 2014 LabKey Corporation
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
package org.labkey.flow.data;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.assay.AssayProviderSchema;

/**
 * User: kevink
 * Date: 4/10/14
 */
public class FlowProviderSchema extends AssayProviderSchema
{
    public FlowProviderSchema(User user, Container container, FlowAssayProvider provider, Container targetStudy)
    {
        super(user, container, provider, targetStudy);
        // Issue 19812: Flow Assay appears in schema browser
        _hidden = true;
    }
}
