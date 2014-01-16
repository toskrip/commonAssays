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

package org.labkey.microarray.matrix;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.Lookup;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineProvider;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.study.actions.AssayRunUploadForm;
import org.labkey.api.study.assay.AbstractAssayProvider;
import org.labkey.api.study.assay.AssayDataType;
import org.labkey.api.study.assay.AssayProtocolSchema;
import org.labkey.api.study.assay.AssayProviderSchema;
import org.labkey.api.study.assay.AssayTableMetadata;
import org.labkey.api.study.assay.ParticipantVisitResolverType;
import org.labkey.api.util.FileType;
import org.labkey.api.util.Pair;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.microarray.MicroarrayModule;
import org.labkey.microarray.query.MicroarrayUserSchema;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ExpressionMatrixAssayProvider extends AbstractAssayProvider
{
    public static final String NAME = "Expression Matrix";
    public static final String RESOURCE_NAME = "ExpressionMatrix";

    public static final String LSID_PREFIX = "ExpressionMatrix";
    public static final AssayDataType DATA_TYPE = new AssayDataType(LSID_PREFIX, new FileType(".tsv"));

    public static final ColumnMappingProperty FEATURE_ANNOTATION_SET_ID_COLUMN = new ColumnMappingProperty("featureSet", "Feature Annotation Set", true);

    public ExpressionMatrixAssayProvider()
    {
        super(LSID_PREFIX, LSID_PREFIX, DATA_TYPE, ModuleLoader.getInstance().getModule(MicroarrayModule.class));
    }

    @Override @NotNull
    public AssayTableMetadata getTableMetadata(@NotNull ExpProtocol protocol)
    {
        return new AssayTableMetadata(this, protocol, null, FieldKey.fromParts("DataId", "Run"), FieldKey.fromParts("RowId"));
    }

    @Override
    public ExpData getDataForDataRow(Object dataRowId, ExpProtocol protocol)
    {
        return null;
    }

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public String getResourceName()
    {
        return RESOURCE_NAME;
    }

    @Override
    public HttpView getDataDescriptionView(AssayRunUploadForm form)
    {
        return new HtmlView("");
    }

    @Override
    protected Pair<Domain, Map<DomainProperty, Object>> createBatchDomain(Container c, User user)
    {
        return super.createBatchDomain(c, user, false);
    }

    @Override
    public AssayProtocolSchema createProtocolSchema(User user, Container container, @NotNull ExpProtocol protocol, @Nullable Container targetStudy)
    {
        return new ExpressionMatrixProtocolSchema(user, container, protocol, targetStudy);
    }

    @Override
    public List<ParticipantVisitResolverType> getParticipantVisitResolverTypes()
    {
        return Collections.emptyList();
    }

    @Override
    public Domain getResultsDomain(ExpProtocol protocol)
    {
        return null;
    }

    @Override
    public PipelineProvider getPipelineProvider()
    {
        return null;
    }

    @Override
    public String getDescription()
    {
        return "Import a GEO series matix-like TSV file of probe/sample values.";
    }

    @Override
    protected Pair<Domain, Map<DomainProperty, Object>> createRunDomain(Container c, User user)
    {
        Pair<Domain, Map<DomainProperty, Object>> result = super.createRunDomain(c, user);
        Domain runDomain = result.getKey();
        Container lookupContainer = c.getProject();

        DomainProperty featureSet = addProperty(runDomain, FEATURE_ANNOTATION_SET_ID_COLUMN.getName(), FEATURE_ANNOTATION_SET_ID_COLUMN.getLabel(), PropertyType.INTEGER);
        featureSet.setLookup(new Lookup(lookupContainer, MicroarrayUserSchema.SCHEMA_NAME, MicroarrayUserSchema.TABLE_FEATURE_ANNOTATION_SET));
        featureSet.setShownInInsertView(true);
        featureSet.setShownInUpdateView(false);
        featureSet.setRequired(true);

        return result;
    }

    @Override
    protected Map<String, Set<String>> getRequiredDomainProperties()
    {
        Map<String, Set<String>> domainMap = super.getRequiredDomainProperties();
        Set<String> runProperties = domainMap.get(ExpProtocol.ASSAY_DOMAIN_RUN);

        if (runProperties == null)
        {
            runProperties = new HashSet<>();
            domainMap.put(ExpProtocol.ASSAY_DOMAIN_RUN, runProperties);
        }

        runProperties.add(FEATURE_ANNOTATION_SET_ID_COLUMN.getName());

        return domainMap;
    }

}