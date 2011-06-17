/*
 * Copyright (c) 2009-2011 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.labkey.nab;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.qc.PlateBasedDataExchangeHandler;
import org.labkey.api.study.PlateTemplate;
import org.labkey.api.study.WellGroup;
import org.labkey.api.study.assay.AbstractPlateBasedAssayProvider;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayRunUploadContext;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.view.ViewContext;

import java.io.File;
import java.util.Map;

/**
 * User: klum
 * Date: Apr 14, 2009
 * Time: 10:30:43 AM
 */
public class NabDataExchangeHandler extends PlateBasedDataExchangeHandler
{
    @Override
    public File createValidationRunInfo(AssayRunUploadContext context, ExpRun run, File scriptDir) throws Exception
    {
        NabRunUploadForm form = (NabRunUploadForm)context;

        Map<String, Map<DomainProperty, String>>props = form.getSampleProperties();

        NabAssayProvider provider = form.getProvider();
        PlateTemplate template = provider.getPlateTemplate(form.getContainer(), form.getProtocol());

        // add in the specimen information, the data will be serialized to a tsv and the file
        // location will be added to the run properties file.

        addSampleProperties(SAMPLE_DATA_PROP_NAME, GROUP_COLUMN_NAME, props, template, WellGroup.Type.SPECIMEN);

        return super.createValidationRunInfo(context, run, scriptDir);
    }

    @Override
    public void createSampleData(@NotNull ExpProtocol protocol, ViewContext viewContext, File scriptDir) throws Exception
    {
        AssayProvider provider = AssayService.get().getProvider(protocol);
        if (provider instanceof AbstractPlateBasedAssayProvider)
        {
            AbstractPlateBasedAssayProvider plateProvider = (AbstractPlateBasedAssayProvider)provider;
            PlateTemplate template = plateProvider.getPlateTemplate(viewContext.getContainer(), protocol);
            DomainProperty[] props = plateProvider.getSampleWellGroupDomain(protocol).getProperties();

            Map<String, Map<DomainProperty, String>>specimens = createTestSampleProperties(props, template, WellGroup.Type.SPECIMEN);

            addSampleProperties(SAMPLE_DATA_PROP_NAME, GROUP_COLUMN_NAME, specimens, template, WellGroup.Type.SPECIMEN);
        }
        super.createSampleData(protocol, viewContext, scriptDir);
    }
}
