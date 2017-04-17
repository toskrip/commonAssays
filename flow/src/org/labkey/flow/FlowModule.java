/*
 * Copyright (c) 2005-2016 Fred Hutchinson Cancer Research Center
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

package org.labkey.flow;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.files.FileContentService;
import org.labkey.api.files.TableUpdaterFileListener;
import org.labkey.api.flow.api.FlowService;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.FolderTypeManager;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.reports.ReportService;
import org.labkey.api.search.SearchService;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.DefaultWebPartFactory;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;
import org.labkey.flow.analysis.model.CompensationMatrix;
import org.labkey.flow.analysis.model.FCSHeader;
import org.labkey.flow.analysis.model.FlowJoWorkspace;
import org.labkey.flow.analysis.model.PopulationName;
import org.labkey.flow.analysis.web.StatisticSpec;
import org.labkey.flow.analysis.web.SubsetParser;
import org.labkey.flow.analysis.web.SubsetTests;
import org.labkey.flow.controllers.FlowController;
import org.labkey.flow.controllers.ReportsController;
import org.labkey.flow.controllers.attribute.AttributeController;
import org.labkey.flow.controllers.compensation.CompensationController;
import org.labkey.flow.controllers.editscript.ScriptController;
import org.labkey.flow.controllers.executescript.AnalysisScriptController;
import org.labkey.flow.controllers.protocol.ProtocolController;
import org.labkey.flow.controllers.run.RunController;
import org.labkey.flow.controllers.well.WellController;
import org.labkey.flow.data.FlowAssayProvider;
import org.labkey.flow.data.FlowDataType;
import org.labkey.flow.data.FlowProperty;
import org.labkey.flow.data.FlowProtocolImplementation;
import org.labkey.flow.persist.AnalysisSerializer;
import org.labkey.flow.persist.FlowContainerListener;
import org.labkey.flow.persist.FlowDataHandler;
import org.labkey.flow.persist.FlowManager;
import org.labkey.flow.persist.PersistTests;
import org.labkey.flow.query.FlowSchema;
import org.labkey.flow.reports.ControlsQCReport;
import org.labkey.flow.reports.PositivityFlowReport;
import org.labkey.flow.script.FlowPipelineProvider;
import org.labkey.flow.webparts.AnalysesWebPart;
import org.labkey.flow.webparts.AnalysisScriptsWebPart;
import org.labkey.flow.webparts.FlowFolderType;
import org.labkey.flow.webparts.FlowSummaryWebPart;
import org.labkey.flow.webparts.OverviewWebPart;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class FlowModule extends DefaultModule
{
    public static final String NAME = "Flow";

    public String getName()
    {
        return "Flow";
    }

    public double getVersion()
    {
        return 17.10;
    }

    protected void init()
    {
        DefaultSchema.registerProvider(FlowSchema.SCHEMANAME, new DefaultSchema.SchemaProvider(this)
        {
            @Override
            public boolean isAvailable(DefaultSchema schema, Module module)
            {
                // UNDONE: schema must be available for backwards campatibility, but consider hiding it instead
                return true;
            }

            public QuerySchema createSchema(DefaultSchema schema, Module module)
            {
                if (HttpView.hasCurrentView())
                {
                    // Use the user and container of the parent schema and the URL parameters from the current context.
                    ViewContext context = HttpView.currentContext();
                    if (context != null)
                        return new FlowSchema(schema.getUser(), schema.getContainer(), context.getActionURL(), context.getRequest(), context.getContainer());
                }

                return new FlowSchema(schema.getUser(), schema.getContainer());
            }
        });
        addController("flow", FlowController.class);
        addController("flow-executescript", AnalysisScriptController.class);
        addController("flow-run", RunController.class);
        addController("flow-editscript", ScriptController.class);
        addController("flow-well", WellController.class);
        addController("flow-compensation", CompensationController.class);
        addController("flow-protocol", ProtocolController.class);
        addController("flow-reports", ReportsController.class);

        addController("flow-attribute", AttributeController.class);

        FlowProperty.register();
        ContainerManager.addContainerListener(new FlowContainerListener());

        ReportService.get().registerReport(new ControlsQCReport());
        ReportService.get().registerReport(new PositivityFlowReport());

        ServiceRegistry.get().registerService(FlowService.class, new FlowServiceImpl());
    }

    @NotNull
    protected Collection<WebPartFactory> createWebPartFactories()
    {
        return Arrays.asList(
                OverviewWebPart.FACTORY,
                AnalysesWebPart.FACTORY,
                AnalysisScriptsWebPart.FACTORY,
                FlowSummaryWebPart.FACTORY,
                new DefaultWebPartFactory("Flow Reports", ReportsController.BeginView.class)
                );
    }

    public boolean hasScripts()
    {
        return true;
    }

    static public boolean isActive(Container container)
    {
        for (Module module : container.getActiveModules())
        {
            if (module instanceof FlowModule)
                return true;
        }
        return false;
    }

    public void doStartup(ModuleContext moduleContext)
    {
        PipelineService.get().registerPipelineProvider(new FlowPipelineProvider(this));
        FlowDataType.register();
        ExperimentService.get().registerExperimentDataHandler(FlowDataHandler.instance);
        FlowProtocolImplementation.register();
        AssayService.get().registerAssayProvider(new FlowAssayProvider());

        FolderTypeManager.get().registerFolderType(this, new FlowFolderType(this));
        SearchService ss = SearchService.get();
        if (null != ss)
            ss.addDocumentParser(FCSHeader.documentParser);
        FlowController.registerAdminConsoleLinks();

        ServiceRegistry.get(FileContentService.class).addFileListener(new TableUpdaterFileListener(FlowManager.get().getTinfoObject(), "uri", TableUpdaterFileListener.Type.uri, "RowId"));
    }


    @Override
    @NotNull
    public Set<String> getSchemaNames()
    {
        return PageFlowUtil.set(FlowManager.get().getSchemaName());
    }


    @Override
    @NotNull
    public Set<Class> getUnitTests()
    {
        return new HashSet<>(Arrays.asList(
                PopulationName.NameTests.class,
                SubsetParser.TestLexer.class,
                SubsetTests.class,
                StatisticSpec.TestCase.class,
                FlowJoWorkspace.LoadTests.class,
                AnalysisSerializer.TestCase.class,
                CompensationMatrix.TestFCS.class));
    }

    @NotNull
    @Override
    public Set<Class> getIntegrationTests()
    {
        return new HashSet<>(Arrays.asList(
                PersistTests.class,
                FlowController.TestCase.class
        ));
    }

    public static String getShortProductName()
    {
        return "Flow";
    }

    public static String getLongProductName()
    {
        return "LabKey Server";
    }

    @NotNull
    @Override
    protected Collection<String> getInternalJarFilenames()
    {
        Collection<String> result = new ArrayList<>(super.getInternalJarFilenames());
        result.add("flow-engine.jar");
        return result;
    }
}
