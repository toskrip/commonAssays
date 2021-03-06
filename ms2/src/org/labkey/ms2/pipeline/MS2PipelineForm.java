/*
 * Copyright (c) 2007-2012 LabKey Corporation
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
package org.labkey.ms2.pipeline;

import org.labkey.ms2.pipeline.tandem.XTandemPipelineProvider;
import org.labkey.api.pipeline.browse.PipelinePathForm;

/**
 * <code>MS2PipelineForm</code> base class for MS2 pipeline forms.
 */
public class MS2PipelineForm extends PipelinePathForm
{
    private String searchEngine = XTandemPipelineProvider.name;

    public String getSearchEngine()
    {
        return searchEngine;
    }

    public void setSearchEngine(String searchEngine)
    {
        this.searchEngine = searchEngine;
    }
}
