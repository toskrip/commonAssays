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

import org.labkey.api.data.*;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.qc.DataExchangeHandler;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.study.actions.AssayRunUploadForm;
import org.labkey.api.study.actions.AssayHeaderView;
import org.labkey.api.study.assay.*;
import org.labkey.api.study.query.RunListQueryView;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.util.FileType;
import org.labkey.api.view.*;
import org.labkey.api.gwt.client.DefaultValueType;
import org.labkey.api.pipeline.PipelineProvider;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.beans.PropertyValue;

import java.util.*;

/**
 * User: kevink
 * Date: Sep 15, 2009
 */
public class ViabilityAssayProvider extends AbstractAssayProvider
{
    public static final String NAME = "Viability";
    public static final String SPECIMENIDS_PROPERTY_NAME = "SpecimenIDs";
    public static final String SPECIMENIDS_PROPERTY_CAPTION = "Specimen IDs";

    public static final String SAMPLE_NUM_PROPERTY_NAME = "SampleNum";
    public static final String SAMPLE_NUM_PROPERTY_CAPTION = "Sample Number";
    public static final String POOL_ID_PROPERTY_NAME = "PoolID";
    public static final String POOL_ID_PROPERTY_CAPTION = "Pool ID";
    public static final String TOTAL_CELLS_PROPERTY_NAME = "TotalCells";
    public static final String TOTAL_CELLS_PROPERTY_CAPTION = "Total Cells";
    public static final String VIABLE_CELLS_PROPERTY_NAME = "ViableCells";
    public static final String VIABLE_CELLS_PROPERTY_CAPTION = "Viable Cells";
    public static final String ORIGINAL_CELLS_PROPERTY_NAME = "Original";
    public static final String ORIGINAL_CELLS_PROPERTY_CAPTION = "Original Cells";
    public static final String VIABILITY_PROPERTY_NAME = "Viability";
    public static final String RECOVERY_PROPERTY_NAME = "Recovery";

    private static final String RESULT_DOMAIN_NAME = "Result Fields";
    public static final String RESULT_LSID_PREFIX = "ViabilityResult";

    public ViabilityAssayProvider()
    {
        super("ViabilityAssayProtocol", "ViabilityAssayRun", GuavaDataHandler.DATA_TYPE, new ResultsAssayTableMetadata());
    }

    /** Relative from Results table. */
    static class ResultsAssayTableMetadata extends AssayTableMetadata
    {
        public ResultsAssayTableMetadata()
        {
            super(null, FieldKey.fromParts("Run"), FieldKey.fromParts("RowId"));
        }

        @Override
        public FieldKey getSpecimenIDFieldKey()
        {
            // Can't lookup to Specimen ID from Results table
            return null;
        }
    }

    /** Relative from ResultSpecimens table. */
    static class ResultsSpecimensAssayTableMetadata extends AssayTableMetadata
    {
        public ResultsSpecimensAssayTableMetadata()
        {
            super(FieldKey.fromParts("ResultID"), FieldKey.fromParts("ResultID", "Run"), FieldKey.fromParts("ResultID"));
        }

        @Override
        public FieldKey getSpecimenIDFieldKey()
        {
            return FieldKey.fromParts(AbstractAssayProvider.SPECIMENID_PROPERTY_NAME);
        }
    }

    /*package*/ static class ResultDomainProperty
    {
        public String name, label, description;
        public PropertyType type;

        public boolean required = true;
        public boolean hideInUploadWizard = false;
        public boolean editableInUploadWizard = false;
        public int inputLength = 9;
        public String format;

        public ResultDomainProperty(String name, String label, PropertyType type, String description)
        {
            this.name = name;
            this.label = label;
            this.description = description;
            this.type = type;
        }
    }

    /*package*/ static Map<String, ResultDomainProperty> RESULT_DOMAIN_PROPERTIES;

    static {
        ResultDomainProperty[] props = new ResultDomainProperty[] {
            new ResultDomainProperty(SAMPLE_NUM_PROPERTY_NAME, SAMPLE_NUM_PROPERTY_CAPTION, PropertyType.STRING, "Sample number"),

            new ResultDomainProperty(PARTICIPANTID_PROPERTY_NAME, PARTICIPANTID_PROPERTY_CAPTION, PropertyType.STRING, "Used with either " + VISITID_PROPERTY_NAME + " or " + DATE_PROPERTY_NAME + " to identify subject and timepoint for assay."),
            new ResultDomainProperty(VISITID_PROPERTY_NAME,  VISITID_PROPERTY_CAPTION, PropertyType.DOUBLE, "Used with " + PARTICIPANTID_PROPERTY_NAME + " to identify subject and timepoint for assay."),
            new ResultDomainProperty(DATE_PROPERTY_NAME,  DATE_PROPERTY_CAPTION, PropertyType.DATE_TIME, "Used with " + PARTICIPANTID_PROPERTY_NAME + " to identify subject and timepoint for assay."),
            new ResultDomainProperty(SPECIMENIDS_PROPERTY_NAME,  SPECIMENIDS_PROPERTY_CAPTION, PropertyType.STRING, "When a matching specimen exists in a study, can be used to identify subject and timepoint for assay."),

            new ResultDomainProperty(POOL_ID_PROPERTY_NAME, POOL_ID_PROPERTY_CAPTION, PropertyType.STRING, "Unique identifier for each pool of specimens"),
            new ResultDomainProperty(TOTAL_CELLS_PROPERTY_NAME, TOTAL_CELLS_PROPERTY_CAPTION, PropertyType.DOUBLE, "Total cell count"),
            new ResultDomainProperty(VIABLE_CELLS_PROPERTY_NAME, VIABLE_CELLS_PROPERTY_CAPTION, PropertyType.DOUBLE, "Total viable cell count"),
            new ResultDomainProperty(ORIGINAL_CELLS_PROPERTY_NAME, ORIGINAL_CELLS_PROPERTY_CAPTION, PropertyType.DOUBLE, "Original cell count (sum of specimen vials original cell count)"),
            // XXX: not in db, should be in domain?
            new ResultDomainProperty(VIABILITY_PROPERTY_NAME, VIABILITY_PROPERTY_NAME, PropertyType.DOUBLE, "Percent viable cell count"),
            new ResultDomainProperty(RECOVERY_PROPERTY_NAME, RECOVERY_PROPERTY_NAME, PropertyType.DOUBLE, "Percent recovered cell count (viable cells / (sum of specimen vials original cell count)"),
        };

        LinkedHashMap<String, ResultDomainProperty> map = new LinkedHashMap<String, ResultDomainProperty>();
        for (ResultDomainProperty prop : props)
        {
            map.put(prop.name, prop);
        }

        map.get(POOL_ID_PROPERTY_NAME).hideInUploadWizard = true;
        map.get(RECOVERY_PROPERTY_NAME).hideInUploadWizard = true;
        map.get(ORIGINAL_CELLS_PROPERTY_NAME).hideInUploadWizard = true;

        map.get(PARTICIPANTID_PROPERTY_NAME).editableInUploadWizard = true;
        map.get(VISITID_PROPERTY_NAME).editableInUploadWizard = true;
        map.get(DATE_PROPERTY_NAME).editableInUploadWizard = true;
        map.get(SPECIMENIDS_PROPERTY_NAME).editableInUploadWizard = true;

        map.get(VISITID_PROPERTY_NAME).required = false;
        map.get(DATE_PROPERTY_NAME).required = false;

        map.get(SAMPLE_NUM_PROPERTY_NAME).inputLength = 3;
        map.get(VIABILITY_PROPERTY_NAME).format = "#0.0#%";
        map.get(RECOVERY_PROPERTY_NAME).format = "#0.0#%";
        map.get(TOTAL_CELLS_PROPERTY_NAME).format = "0.000E0";
        map.get(VIABLE_CELLS_PROPERTY_NAME).format = "0.000E0";
        map.get(ORIGINAL_CELLS_PROPERTY_NAME).format = "0.000E0";

        RESULT_DOMAIN_PROPERTIES = Collections.unmodifiableMap(map);
    }

    public String getName()
    {
        return NAME;
    }

    public String getDescription()
    {
        return "Imports Guava cell count and viability data.";
    }

    @Override
    public AssaySchema getProviderSchema(User user, Container container, ExpProtocol protocol)
    {
        return new ViabilityAssaySchema(user, container, protocol);
    }

    public HttpView getDataDescriptionView(AssayRunUploadForm form)
    {
        return new HtmlView("Currently the only supported file type is the Guava comma separated values (.csv) file format.");
    }

    public TableInfo createDataTable(AssaySchema schema, ExpProtocol protocol)
    {
        ViabilityAssaySchema viabilitySchema = new ViabilityAssaySchema(schema.getUser(), schema.getContainer(), protocol);
        viabilitySchema.setTargetStudy(schema.getTargetStudy());
        AbstractTableInfo table = viabilitySchema.createResultsTable();
        // UNDONE: add copy to study columns when copy to study is implemented
        //addCopiedToStudyColumns(table, protocol, schema.getUser(), "rowId", true);
        return table;
    }

    public ExpData getDataForDataRow(Object resultRowId)
    {
        if (resultRowId == null)
            return null;

        Integer id;
        if (resultRowId instanceof Integer)
        {
            id = (Integer)resultRowId;
        }
        else
        {
            try
            {
                id = Integer.parseInt(resultRowId.toString());
            }
            catch (NumberFormatException nfe)
            {
                return null;
            }
        }

        return ViabilityManager.getResultExpData(id.intValue());
    }

    @Override
    protected Map<String, Set<String>> getRequiredDomainProperties()
    {
        Map<String, Set<String>> domainMap = super.getRequiredDomainProperties();
        Set<String> propertyNames = new HashSet<String>();
        for (Map.Entry<String, ResultDomainProperty> entry : RESULT_DOMAIN_PROPERTIES.entrySet())
        {
            ResultDomainProperty prop = entry.getValue();
            if (prop.required)
                propertyNames.add(entry.getKey());
        }
        domainMap.put(ExpProtocol.ASSAY_DOMAIN_DATA, propertyNames);
        return domainMap;
    }

    @Override
    public boolean allowDefaultValues(Domain domain)
    {
        return true;
    }

    @Override
    public DefaultValueType[] getDefaultValueOptions(Domain domain)
    {
        Lsid domainLsid = new Lsid(domain.getTypeURI());
        if (ExpProtocol.ASSAY_DOMAIN_DATA.equals(domainLsid.getNamespacePrefix()))
            return new DefaultValueType[] { DefaultValueType.FIXED_EDITABLE, DefaultValueType.FIXED_NON_EDITABLE };
        return super.getDefaultValueOptions(domain);
    }

    @Override
    public DefaultValueType getDefaultValueDefault(Domain domain)
    {
        Lsid domainLsid = new Lsid(domain.getTypeURI());
        if (ExpProtocol.ASSAY_DOMAIN_DATA.equals(domainLsid.getNamespacePrefix()))
            return DefaultValueType.FIXED_EDITABLE;
        return super.getDefaultValueDefault(domain);
    }

    @Override
    public List<Pair<Domain, Map<DomainProperty, Object>>> createDefaultDomains(Container c, User user)
    {
        List<Pair<Domain, Map<DomainProperty, Object>>> result = super.createDefaultDomains(c, user);

        Pair<Domain, Map<DomainProperty, Object>> resultDomain = createResultDomain(c, user);
        if (resultDomain != null)
            result.add(resultDomain);

        return result;
    }

    protected Pair<Domain, Map<DomainProperty, Object>> createResultDomain(Container c, User user)
    {
        String lsid = getPresubstitutionLsid(ExpProtocol.ASSAY_DOMAIN_DATA);
        Domain resultDomain = PropertyService.get().createDomain(c, lsid, RESULT_DOMAIN_NAME);
        resultDomain.setDescription("The user is prompted to enter data values for row of data associated with a run, typically done as uploading a file.  This is part of the second step of the upload process.");

        for (ResultDomainProperty rdp : RESULT_DOMAIN_PROPERTIES.values())
        {
            DomainProperty dp = addProperty(resultDomain, rdp.name, rdp.label, rdp.type, rdp.description);
            if (rdp.format != null)
                dp.setFormat(rdp.format);
            PropertyDescriptor pd = dp.getPropertyDescriptor();
            pd.setInputLength(rdp.inputLength);
        }

        return new Pair<Domain, Map<DomainProperty, Object>>(resultDomain, Collections.<DomainProperty, Object>emptyMap());
    }

    @Override
    protected Pair<Domain, Map<DomainProperty, Object>> createRunDomain(Container c, User user)
    {
        Pair<Domain, Map<DomainProperty, Object>> result = super.createRunDomain(c, user);
        Domain domain = result.first;
        if (domain.getPropertyByName(AbstractAssayProvider.DATE_PROPERTY_NAME) == null)
        {
            addProperty(domain, AbstractAssayProvider.DATE_PROPERTY_NAME, AbstractAssayProvider.DATE_PROPERTY_CAPTION, PropertyType.DATE_TIME, "Date the assay was run.  If not manually entered, the date will be read from the guava assay file.");
        }
        return result;
    }

    public DomainProperty[] getResultDomainUserProperties(ExpProtocol protocol)
    {
        return getResultDomainUserProperties(getResultsDomain(protocol));
    }

    public static DomainProperty[] getResultDomainUserProperties(Domain resultsDomain)
    {
        DomainProperty[] allProperties = resultsDomain.getProperties();
        List<DomainProperty> filtered = new ArrayList<DomainProperty>(allProperties.length);
        for (DomainProperty property : allProperties)
        {
            if (RESULT_DOMAIN_PROPERTIES.containsKey(property.getName()))
                continue;
            filtered.add(property);
        }
        return filtered.toArray(new DomainProperty[filtered.size()]);
    }

    public List<ParticipantVisitResolverType> getParticipantVisitResolverTypes()
    {
        return Collections.emptyList();
    }

    @Override
    public boolean canCopyToStudy()
    {
        return false;
    }

    public ActionURL copyToStudy(User user, ExpProtocol protocol, Container study, Map<Integer, AssayPublishKey> dataKeys, List<String> errors)
    {
        throw new UnsupportedOperationException("NYI");
    }

    @Override
    public DataExchangeHandler getDataExchangeHandler()
    {
        return new ViabilityDataExchangeHandler();
    }

    @Override
    public ActionURL getImportURL(Container container, ExpProtocol protocol)
    {
        return PageFlowUtil.urlProvider(AssayUrls.class).getProtocolURL(container, protocol, ViabilityAssayUploadWizardAction.class);
    }

    @Override
    public RunListQueryView createRunQueryView(ViewContext context, ExpProtocol protocol)
    {
        return new ViabilityRunListQueryView(protocol, context);
    }

    private class ViabilityRunListQueryView extends RunListQueryView
    {
        ExpProtocol _protocol;

        public ViabilityRunListQueryView(ExpProtocol protocol, UserSchema schema, QuerySettings settings, AssayRunType assayRunFilter)
        {
            super(protocol, schema, settings, assayRunFilter);
            _protocol = protocol;
        }

        public ViabilityRunListQueryView(ExpProtocol protocol, ViewContext context)
        {
            super(protocol, context);
            _protocol = protocol;
        }

        @Override
        public List<DisplayColumn> getDisplayColumns()
        {
            ActionURL reRunURL = getImportURL(getContainer(), _protocol);
            reRunURL.addParameter("reRunId", "${RowId}");

            DisplayColumn reRunDisplayCol = new UrlColumn(StringExpressionFactory.createURL(reRunURL), "re-import");
            reRunDisplayCol.setNoWrap(true);

            List<DisplayColumn> cols = super.getDisplayColumns();
            cols.add(0, reRunDisplayCol);
            return cols;
        }
    }

    @Override
    public ModelAndView createResultsView(ViewContext context, ExpProtocol protocol)
    {
        return new AssayResultsView(protocol, false)
        {
            @Override
            protected ModelAndView createHeaderView(QueryView queryView, boolean minimizeLinks, AssayProvider provider, ExpProtocol protocol)
            {
                return new ViabilityDetailsHeaderView(protocol, provider, minimizeLinks, queryView.getTable().getContainerFilter());
            }
        };
    }

    private class ViabilityDetailsHeaderView extends AssayHeaderView
    {
        public ViabilityDetailsHeaderView(ExpProtocol protocol, AssayProvider provider, boolean minimizeLinks, ContainerFilter containerFilter)
        {
            super(protocol, provider, minimizeLinks, containerFilter);
        }

        @Override
        public List<NavTree> getLinks()
        {
            List<NavTree> links = super.getLinks();
            AssayProvider provider = getProvider();
            ExpProtocol protocol = getProtocol();

            // UCK. Getting query filter parameter from url.
            PropertyValue pv = getViewContext().getBindPropertyValues().getPropertyValue(protocol.getName() + " Data.Run/RowId~eq");
            if (pv != null && pv.getValue() != null)
            {
                Object value = pv.getValue();

                int runId = 0;
                if (value instanceof Integer)
                    runId = ((Integer)value).intValue();
                else if (value instanceof String)
                    try
                    {
                        runId = Integer.parseInt((String)value);
                    }
                    catch (NumberFormatException nfe) { }

                Container c = getViewContext().getContainer();
                User user = getViewContext().getUser();

                boolean canInsert = c.hasPermission(user, InsertPermission.class);
                boolean canDelete = c.hasPermission(user, DeletePermission.class);
                if (canInsert)
                {
                    links.add(new NavTree(AbstractAssayProvider.IMPORT_DATA_LINK_NAME, provider.getImportURL(c, protocol)));

                    if (runId > 0)
                    {
                        ActionURL reRunURL = getImportURL(c, protocol);
                        reRunURL.addParameter("reRunId", runId);

                        links.add(new NavTree("re-import", reRunURL));

                        if (canDelete)
                        {
                            ActionURL deleteReRunURL = reRunURL.clone();
                            deleteReRunURL.addParameter("delete", "true");
                            links.add(new NavTree("delete and re-import", deleteReRunURL));
                        }
                    }
                }
            }
            return links;
        }
    }

    public PipelineProvider getPipelineProvider()
    {
        return new AssayPipelineProvider(ViabilityModule.class,
                new PipelineProvider.FileTypesEntryFilter(new FileType(".csv")), this, "Import Viability");
    }
}
