/*
 * Copyright (c) 2005-2010 LabKey Corporation
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

package org.labkey.flow.script;

import org.labkey.api.pipeline.*;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewContext;
import org.labkey.api.module.Module;
import org.labkey.flow.analysis.model.FCS;
import org.labkey.flow.FlowModule;
import org.labkey.flow.controllers.executescript.AnalysisScriptController;
import org.labkey.flow.data.FlowProtocolStep;
import org.labkey.flow.data.FlowRun;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.FileFilter;
import java.sql.SQLException;
import java.util.*;

public class FlowPipelineProvider extends PipelineProvider
{
    public static final String NAME = "flow";

    private static final String IMPORT_DIRECTORY_NAVTREE_LABEL = "Import FCS Files";
    private static final String IMPORT_WORKSPACE_LABEL = "Import FlowJo Workspace";
    private static final String IMPORT_DIRECTORY_LABEL = "Import Directory of FCS Files";
    private static final String IMPORT_ANALYSIS_LABEL = "Import Analysis";

    public FlowPipelineProvider(Module owningModule)
    {
        super(NAME, owningModule);
    }

    private boolean hasFlowModule(ViewContext context)
    {
        return FlowModule.isActive(context.getContainer());
    }

    static class WorkspaceRecognizer extends DefaultHandler
    {
        boolean _isWorkspace = false;

        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException
        {
            _isWorkspace = "Workspace".equals(qName);
            throw new SAXException("Stop parsing");
        }
        boolean isWorkspace()
        {
            return _isWorkspace;
        }
    }

    private class IsFlowJoWorkspaceFilter extends FileEntryFilter
    {
        public boolean accept(File pathname)
        {
            if (pathname.isDirectory())
                return false;
            if (pathname.getName().endsWith(".wsp"))
                return true;
            if (!pathname.getName().endsWith(".xml"))
                return false;
            WorkspaceRecognizer recognizer = new WorkspaceRecognizer();
            try
            {
                SAXParser parser = SAXParserFactory.newInstance().newSAXParser();

                parser.parse(pathname, recognizer);
            }
            catch (Exception e)
            {
                // suppress
            }
            return recognizer.isWorkspace();
        }
    }


    public void updateFileProperties(ViewContext context, final PipeRoot pr, PipelineDirectory directory, boolean includeAll)
    {
        if (!context.getContainer().hasPermission(context.getUser(), InsertPermission.class))
            return;
        if (!hasFlowModule(context))
            return;

        final Set<String> usedRelativePaths = new HashSet<String>();

        try
        {
            for (FlowRun run : FlowRun.getRunsForContainer(context.getContainer(), FlowProtocolStep.keywords))
                usedRelativePaths.add(pr.relativePath(run.getExperimentRun().getFilePathRoot()));
        }
        catch (SQLException e)
        {
            return;
        }

        // UNDONE: walk directory once instead of multiple times
        File[] dirs = directory.listFiles(new FileFilter()
        {
            public boolean accept(File dir)
            {
                if (!dir.isDirectory())
                    return false;

                if (usedRelativePaths.contains(pr.relativePath(dir)))
                    return false;

                File[] fcsFiles = dir.listFiles((FileFilter)FCS.FCSFILTER);
                return null != fcsFiles && 0 != fcsFiles.length;
            }
        });

        ActionURL importRunsURL = new ActionURL(AnalysisScriptController.ConfirmImportRunsAction.class, context.getContainer());

        String path = directory.getPathParameter();
        ActionURL returnUrl = PageFlowUtil.urlProvider(PipelineUrls.class).urlBrowse(context.getContainer(), null, path.toString());
        importRunsURL.addReturnURL(returnUrl);
        importRunsURL.replaceParameter("path", path);
        String baseId = this.getClass().getName();

        if (includeAll || (dirs != null && dirs.length > 0))
        {
            NavTree selectedDirsNavTree = new NavTree(IMPORT_DIRECTORY_NAVTREE_LABEL);
            selectedDirsNavTree.setId(baseId);

            NavTree child = new NavTree(IMPORT_DIRECTORY_LABEL, importRunsURL);
            String actionId = createActionId(this.getClass(), IMPORT_DIRECTORY_LABEL);
            child.setId(actionId);

            selectedDirsNavTree.addChild(child);
            directory.addAction(new PipelineAction(selectedDirsNavTree, dirs, true, true));
        }

        if (includeAll || !usedRelativePaths.contains(directory.getRelativePath()))
        {
            // UNDONE: walk directory once instead of multiple times
            File[] fcsFiles = directory.listFiles(FCS.FCSFILTER);
            if (includeAll || (fcsFiles != null && fcsFiles.length > 0))
            {
                ActionURL url = importRunsURL.clone().addParameter("current", true);
                NavTree tree = new NavTree(IMPORT_DIRECTORY_NAVTREE_LABEL);
                tree.setId(baseId);

                NavTree child = new NavTree("Current directory of " + (fcsFiles.length > 0 ? fcsFiles.length : "") + " FCS Files", url);
                child.setId(baseId + ":FCS Files");
                tree.addChild(child);

                directory.addAction(new PipelineAction(tree, new File[] { pr.resolvePath(directory.getRelativePath()) }, false, true));
            }
        }

        // UNDONE: walk directory once instead of multiple times
        File[] workspaces = directory.listFiles(new IsFlowJoWorkspaceFilter());
        if (includeAll || (workspaces != null && workspaces.length > 0))
        {
            ActionURL importWorkspaceURL = new ActionURL(AnalysisScriptController.ImportAnalysisFromPipelineAction.class, context.getContainer());
            importWorkspaceURL.replaceParameter("path", path);
            String actionId = createActionId(AnalysisScriptController.ImportAnalysisFromPipelineAction.class, IMPORT_WORKSPACE_LABEL);
            addAction(actionId, importWorkspaceURL, IMPORT_WORKSPACE_LABEL, directory, workspaces, false, true, includeAll);

            // UNDONE: create workspace from FlowJo workspace
        }

        // UNDONE: import FlowJo exported compensation matrix file: CompensationController.UploadAction


        // UNDONE: walk directory once instead of multiple times
        File[] summaryStats = directory.listFiles(new FileFilter() {
            @Override
            public boolean accept(File file)
            {
                return file.getName().equalsIgnoreCase(RImportJob.SUMMARY_STATS_FILENAME);
            }
        });
        if (includeAll || (summaryStats != null && summaryStats.length > 0))
        {
            String actionId = createActionId(AnalysisScriptController.ImportAnalysisResultsAction.class, IMPORT_ANALYSIS_LABEL);
            addAction(actionId, AnalysisScriptController.ImportAnalysisResultsAction.class, IMPORT_ANALYSIS_LABEL, directory, summaryStats, false, true, includeAll);
        }
    }

    @Override
    public List<PipelineActionConfig> getDefaultActionConfig()
    {
        List<PipelineActionConfig> configs = new ArrayList<PipelineActionConfig>();

        // import directory of fcs files
        String actionId = createActionId(this.getClass(), null);
        PipelineActionConfig importConfig = new PipelineActionConfig(actionId, PipelineActionConfig.displayState.enabled, IMPORT_DIRECTORY_NAVTREE_LABEL);

        actionId = createActionId(this.getClass(), IMPORT_DIRECTORY_LABEL);
        importConfig.setLinks(Collections.singletonList(new PipelineActionConfig(actionId, PipelineActionConfig.displayState.toolbar, IMPORT_DIRECTORY_LABEL)));
        configs.add(importConfig);

        // import flow workspace
        actionId = createActionId(AnalysisScriptController.ImportAnalysisFromPipelineAction.class, IMPORT_WORKSPACE_LABEL);
        configs.add(new PipelineActionConfig(actionId, PipelineActionConfig.displayState.toolbar, IMPORT_WORKSPACE_LABEL, true));

        return configs;
    }


    public boolean suppressOverlappingRootsWarning(ViewContext context)
    {
        if (!hasFlowModule(context))
            return super.suppressOverlappingRootsWarning(context);
        return true;
    }
}
