/*
 * Copyright (c) 2008-2011 LabKey Corporation
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

package org.labkey.elispot;

import org.labkey.api.data.*;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.OntologyObject;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.*;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListItem;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.Lookup;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.exp.query.ExpRunTable;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.study.actions.AssayRunUploadForm;
import org.labkey.api.study.assay.*;
import org.labkey.api.study.query.ResultsQueryView;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.*;
import org.labkey.api.qc.DataExchangeHandler;
import org.labkey.api.util.Pair;
import org.labkey.api.pipeline.PipelineProvider;
import org.labkey.elispot.plate.ElispotPlateReaderService;
import org.labkey.elispot.plate.ExcelPlateReader;
import org.labkey.elispot.plate.TextPlateReader;
import org.labkey.elispot.query.ElispotRunDataTable;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Jan 7, 2008
 */
public class ElispotAssayProvider extends AbstractPlateBasedAssayProvider
{
    public static final String NAME = "ELISpot";
    public static final String ASSAY_DOMAIN_ANTIGEN_WELLGROUP = ExpProtocol.ASSAY_DOMAIN_PREFIX + "AntigenWellGroup";

    // run properties
    public static final String READER_PROPERTY_NAME = "PlateReader";
    public static final String READER_PROPERTY_CAPTION = "Plate Reader";

    // sample well groups
    public static final String SAMPLE_DESCRIPTION_PROPERTY_NAME = "SampleDescription";
    public static final String SAMPLE_DESCRIPTION_PROPERTY_CAPTION = "Sample Description";
    public static final String EFFECTOR_PROPERTY_NAME = "Effector";
    public static final String EFFECTOR_PROPERTY_CAPTION = "Effector Cell";
    public static final String STCL_PROPERTY_NAME = "STCL";
    public static final String STCL_PROPERTY_CAPTION = "Stimulation Antigen";

    // antigen well groups
    public static final String CELLWELL_PROPERTY_NAME = "CellWell";
    public static final String CELLWELL_PROPERTY_CAPTION = "Cells per Well";
    public static final String ANTIGENID_PROPERTY_NAME = "AntigenID";
    public static final String ANTIGENID_PROPERTY_CAPTION = "Antigen ID";
    public static final String ANTIGENNAME_PROPERTY_NAME = "AntigenName";
    public static final String ANTIGENNAME_PROPERTY_CAPTION = "Antigen Name";
    public static final String PEPTIDE_CONCENTRATION_NAME = "PeptideConcentration";
    public static final String PEPTIDE_CONCENTRATION_CAPTION = "Peptide Concentration (ug/ml)";


    public ElispotAssayProvider()
    {
        super("ElispotAssayProtocol", "ElispotAssayRun", ElispotDataHandler.ELISPOT_DATA_TYPE, new AssayTableMetadata(
            FieldKey.fromParts("Properties", ElispotDataHandler.ELISPOT_INPUT_MATERIAL_DATA_PROPERTY, "Property"),
            FieldKey.fromParts("Run"),
            FieldKey.fromParts("ObjectId")));
    }

    public ExpData getDataForDataRow(Object dataRowId, ExpProtocol protocol)
    {
        if (!(dataRowId instanceof Integer))
            return null;
        OntologyObject dataRow = OntologyManager.getOntologyObject((Integer) dataRowId);
        if (dataRow == null)
            return null;
        OntologyObject dataRowParent = OntologyManager.getOntologyObject(dataRow.getOwnerObjectId());
        if (dataRowParent == null)
            return null;
        return ExperimentService.get().getExpData(dataRowParent.getObjectURI());
    }

    public String getName()
    {
        return NAME;
    }

    public Domain getResultsDomain(ExpProtocol protocol)
    {
        return null;
    }

    public List<Pair<Domain, Map<DomainProperty, Object>>> createDefaultDomains(Container c, User user)
    {
        List<Pair<Domain, Map<DomainProperty, Object>>> result = super.createDefaultDomains(c, user);
        result.add(createAntigenWellGroupDomain(c, user));
        return result;
    }
    
    public HttpView getDataDescriptionView(AssayRunUploadForm form)
    {
        return new HtmlView("The Elispot data file is the output file from the plate reader that has been selected.");
    }

    public ElispotRunDataTable createDataTable(AssaySchema schema, ExpProtocol protocol, boolean includeCopiedToStudyColumns)
    {
        ElispotRunDataTable table = new ElispotRunDataTable(schema, protocol);
        if (includeCopiedToStudyColumns)
        {
            addCopiedToStudyColumns(table, protocol, schema.getUser(), true);
        }
        return table;
    }

    protected Pair<Domain, Map<DomainProperty, Object>> createSampleWellGroupDomain(Container c, User user)
    {
        Pair<Domain, Map<DomainProperty, Object>> result = super.createSampleWellGroupDomain(c, user);

        Domain domain = result.getKey();
        addProperty(domain, SPECIMENID_PROPERTY_NAME, SPECIMENID_PROPERTY_CAPTION, PropertyType.STRING);
        addProperty(domain, PARTICIPANTID_PROPERTY_NAME, PARTICIPANTID_PROPERTY_CAPTION, PropertyType.STRING);
        addProperty(domain, VISITID_PROPERTY_NAME, VISITID_PROPERTY_CAPTION, PropertyType.DOUBLE);
        addProperty(domain, DATE_PROPERTY_NAME, DATE_PROPERTY_CAPTION, PropertyType.DATE_TIME);
        addProperty(domain, SAMPLE_DESCRIPTION_PROPERTY_NAME, SAMPLE_DESCRIPTION_PROPERTY_CAPTION, PropertyType.STRING);
        //addProperty(sampleWellGroupDomain, EFFECTOR_PROPERTY_NAME, EFFECTOR_PROPERTY_CAPTION, PropertyType.STRING);
        //addProperty(sampleWellGroupDomain, STCL_PROPERTY_NAME, STCL_PROPERTY_CAPTION, PropertyType.STRING);

        return result;
    }

    protected Pair<Domain, Map<DomainProperty, Object>> createAntigenWellGroupDomain(Container c, User user)
    {
        String domainLsid = getPresubstitutionLsid(ASSAY_DOMAIN_ANTIGEN_WELLGROUP);
        Domain antigenWellGroupDomain = PropertyService.get().createDomain(c, domainLsid, "Antigen Fields");

        antigenWellGroupDomain.setDescription("The user will be prompted to enter these properties for each of the antigen well groups in their chosen plate template.");
        addProperty(antigenWellGroupDomain, ANTIGENID_PROPERTY_NAME, ANTIGENID_PROPERTY_CAPTION, PropertyType.INTEGER);
        addProperty(antigenWellGroupDomain, ANTIGENNAME_PROPERTY_NAME, ANTIGENNAME_PROPERTY_CAPTION, PropertyType.STRING).setDimension(true);
        addProperty(antigenWellGroupDomain, CELLWELL_PROPERTY_NAME, CELLWELL_PROPERTY_CAPTION, PropertyType.INTEGER);
        //addProperty(antigenWellGroupDomain, PEPTIDE_CONCENTRATION_NAME, PEPTIDE_CONCENTRATION_CAPTION, PropertyType.DOUBLE);

        return new Pair<Domain, Map<DomainProperty, Object>>(antigenWellGroupDomain, Collections.<DomainProperty, Object>emptyMap());
    }

    protected Pair<Domain,Map<DomainProperty,Object>> createRunDomain(Container c, User user)
    {
        Pair<Domain, Map<DomainProperty, Object>> result =  super.createRunDomain(c, user);
        Domain runDomain = result.getKey();

        addProperty(runDomain, "ProtocolName", "ProtocolName", PropertyType.STRING);
        addProperty(runDomain, "LabID", "Lab ID", PropertyType.STRING);
        addProperty(runDomain, "PlateID", "Plate ID", PropertyType.STRING);
        addProperty(runDomain, "TemplateID", "Template ID", PropertyType.STRING);
        addProperty(runDomain, "ExperimentDate", "Experiment Date", PropertyType.DATE_TIME);

        ListDefinition plateReaderList = createPlateReaderList(c, user);
        DomainProperty reader = addProperty(runDomain, READER_PROPERTY_NAME, READER_PROPERTY_CAPTION, PropertyType.STRING);
        reader.setLookup(new Lookup(c.getProject(), "lists", plateReaderList.getName()));
        reader.setRequired(true);

        return result;
    }

    private ListDefinition createPlateReaderList(Container c, User user)
    {
        ListDefinition readerList = ElispotPlateReaderService.getPlateReaderList(c);
        if (readerList == null)
        {
            readerList = ElispotPlateReaderService.createPlateReaderList(c, user);

            DomainProperty nameProperty = readerList.getDomain().getPropertyByName(ElispotPlateReaderService.PLATE_READER_PROPERTY);
            DomainProperty typeProperty = readerList.getDomain().getPropertyByName(ElispotPlateReaderService.READER_TYPE_PROPERTY);

            try {
                addItem(readerList, user, "Cellular Technology Ltd. (CTL)", nameProperty, ExcelPlateReader.TYPE, typeProperty);
                addItem(readerList, user, "AID", nameProperty, TextPlateReader.TYPE, typeProperty);
                addItem(readerList, user, "Zeiss", nameProperty, TextPlateReader.TYPE, typeProperty);
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }
        return readerList;
    }

    private void addItem(ListDefinition list, User user, String name, DomainProperty nameProperty,
                         String fileType, DomainProperty fileTypeProperty) throws Exception
    {
        ListItem reader = list.createListItem();
        reader.setKey(name);
        reader.setProperty(nameProperty, name);
        reader.setProperty(fileTypeProperty, fileType);
        reader.save(user);
    }

    public List<ParticipantVisitResolverType> getParticipantVisitResolverTypes()
    {
        return Arrays.asList(new ParticipantVisitLookupResolverType(), new SpecimenIDLookupResolverType(), new ParticipantDateLookupResolverType(), new ThawListResolverType());
    }

    public ActionURL getImportURL(Container container, ExpProtocol protocol)
    {
        return PageFlowUtil.urlProvider(AssayUrls.class).getProtocolURL(container, protocol, ElispotUploadWizardAction.class);
    }

    public Domain getAntigenWellGroupDomain(ExpProtocol protocol)
    {
        return getDomainByPrefix(protocol, ASSAY_DOMAIN_ANTIGEN_WELLGROUP);
    }

    private static final class ElispotResultsQueryView extends ResultsQueryView
    {
        public ElispotResultsQueryView(ExpProtocol protocol, ViewContext context, QuerySettings settings)
        {
            super(protocol, context, settings);
        }

        public DataView createDataView()
        {
            DataView view = super.createDataView();
            view.getDataRegion().setRecordSelectorValueColumns("ObjectId");
            return view;
        }
    }

    public ElispotResultsQueryView createResultsQueryView(ViewContext context, ExpProtocol protocol)
    {
        UserSchema schema = AssayService.get().createSchema(context.getUser(), context.getContainer());
        String name = AssayService.get().getResultsTableName(protocol);
        QuerySettings settings = schema.getSettings(context, name, name);
        return new ElispotResultsQueryView(protocol, context, settings);
    }

    public RunListDetailsQueryView createRunQueryView(ViewContext context, ExpProtocol protocol)
    {
        return new RunListDetailsQueryView(protocol, context,
                ElispotController.RunDetailRedirectAction.class, "rowId", ExpRunTable.Column.RowId.toString());
    }

    public String getDescription()
    {
        return "Imports raw data files from CTL and AID instruments.";
    }

    @Override
    public DataExchangeHandler createDataExchangeHandler()
    {
        return new ElispotDataExchangeHandler();
    }

    @Override
    public AssaySchema getProviderSchema(User user, Container container, ExpProtocol protocol)
    {
        return new ElispotSchema(user, container, protocol);
    }

    public PipelineProvider getPipelineProvider()
    {
        return new AssayPipelineProvider(ElispotModule.class,
                new PipelineProvider.FileTypesEntryFilter(ElispotDataHandler.ELISPOT_DATA_TYPE.getFileType()),
                this, "Import ELISpot");
    }
}
