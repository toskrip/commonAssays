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

import org.labkey.api.pipeline.PipelineValidationException;
import org.labkey.api.pipeline.file.AbstractFileAnalysisProvider;
import org.labkey.api.pipeline.TaskPipeline;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.module.Module;

import java.io.IOException;
import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * <code>AbstractMS2SearchPipelineProvider</code>
 */
abstract public class AbstractMS2SearchPipelineProvider
        extends AbstractFileAnalysisProvider<AbstractMS2SearchProtocolFactory, TaskPipeline>
{
    public AbstractMS2SearchPipelineProvider(String name, Module owningModule)
    {
        super(name, owningModule);
    }

    public void initSystemDirectory(File rootDir, File systemDir)
    {
        AbstractMS2SearchProtocolFactory factory = getProtocolFactory();
        if (factory != null)
            factory.initSystemDirectory(rootDir, systemDir);
    }

    public AbstractMS2SearchProtocolFactory getProtocolFactory(TaskPipeline pipeline)
    {
        // MS2 search providers all support only one protocol factory each.
        return getProtocolFactory();
    }

    public AbstractMS2SearchProtocolFactory getProtocolFactory(File file)
    {
        AbstractMS2SearchProtocolFactory factory = getProtocolFactory();
        if (factory != null && factory.isProtocolTypeFile(file))
            return factory;
        return null; 
    }

    public boolean dbExists(File sequenceRoot, String db)
    {
        File dbFile = new File(sequenceRoot, db);
        return NetworkDrive.exists(dbFile);
    }

    abstract public boolean supportsDirectories();

    abstract public boolean remembersDirectories();

    abstract public boolean hasRemoteDirectories();

    abstract public AbstractMS2SearchProtocolFactory getProtocolFactory();

    abstract public void ensureEnabled() throws PipelineValidationException;

    abstract public List<String> getSequenceDbPaths(File sequenceRoot) throws IOException;

    abstract public List<String> getSequenceDbDirList(File sequenceRoot) throws IOException;

    abstract public List<String> getTaxonomyList() throws IOException;

    abstract public Map<String, String> getEnzymes() throws IOException;

    abstract public Map<String, String> getResidue0Mods() throws IOException;

    abstract public Map<String, String> getResidue1Mods() throws IOException;

    abstract public String getHelpTopic();
}
