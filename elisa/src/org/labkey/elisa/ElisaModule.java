/*
 * Copyright (c) 2012-2013 LabKey Corporation
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

package org.labkey.elisa;

import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.study.PlateService;
import org.labkey.api.study.assay.AbstractPlateBasedAssayProvider;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.study.assay.plate.PlateReaderService;
import org.labkey.api.view.WebPartFactory;
import org.labkey.elisa.plate.BioTekPlateReader;

import java.util.Collection;
import java.util.Collections;

public class ElisaModule extends DefaultModule
{
    @Override
    public String getName()
    {
        return "Elisa";
    }

    @Override
    public double getVersion()
    {
        return 13.10;
    }

    @Override
    public boolean hasScripts()
    {
        return false;
    }

    @Override
    protected Collection<WebPartFactory> createWebPartFactories()
    {
        return Collections.emptyList();
    }

    @Override
    protected void init()
    {
        addController("elisa", ElisaController.class);
    }

    @Override
    public void doStartup(ModuleContext moduleContext)
    {
        // add a container listener so we'll know when our container is deleted:
        ContainerManager.addContainerListener(new ElisaContainerListener());

        PlateService.get().registerPlateTypeHandler(new ElisaPlateTypeHandler());
        ExperimentService.get().registerExperimentDataHandler(new ElisaDataHandler());

        AbstractPlateBasedAssayProvider provider = new ElisaAssayProvider();

        AssayService.get().registerAssayProvider(provider);
        PlateReaderService.registerPlateReader(provider, new BioTekPlateReader());
    }

    @Override
    public Collection<String> getSummary(Container c)
    {
        return Collections.emptyList();
    }
}