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
package org.labkey.test.ms2;

import org.labkey.test.pipeline.PipelineFolder;
import org.labkey.test.pipeline.PipelineWebTestBase;

import java.io.File;

/**
 * <code>MS2PipelineFolder</code>
 */
public class MS2PipelineFolder extends PipelineFolder
{
    public MS2PipelineFolder(PipelineWebTestBase test,
                             String folderName,
                             File pipelinePath)
    {
        super(test, folderName, pipelinePath);
        setFolderType("MS2");   // Default to MS2 dashboard
    }
}
