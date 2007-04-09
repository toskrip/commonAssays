package org.labkey.flow.controllers.executescript;

import org.labkey.flow.controllers.BaseFlowController;
import org.apache.beehive.netui.pageflow.Forward;
import org.apache.beehive.netui.pageflow.annotations.Jpf;
import org.apache.struts.action.ActionError;
import org.apache.commons.lang.StringUtils;
import org.labkey.flow.data.*;
import org.labkey.flow.script.*;
import org.labkey.flow.analysis.model.FlowJoWorkspace;
import org.labkey.flow.analysis.model.FCSKeywordData;
import org.labkey.flow.persist.AttributeSet;
import org.labkey.flow.persist.ObjectType;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.security.ACL;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URIUtil;
import org.labkey.api.view.*;
import org.labkey.api.jsp.FormPage;
import org.labkey.api.exp.api.*;
import org.labkey.api.study.GenericAssayService;

import java.io.File;
import java.io.FileInputStream;
import java.util.*;
import java.net.URI;

@Jpf.Controller(messageBundles = {@Jpf.MessageBundle(bundlePath = "messages.Validation")})
public class AnalysisScriptController extends BaseFlowController<AnalysisScriptController.Action>
{
    public enum Action
    {
        begin,

        showUploadRuns,
        chooseRunsToUpload,
        uploadRuns,

        chooseRunsToAnalyze,
        chooseAnalysisName,
        analyzeSelectedRuns,

        uploadWorkspace,

        showRefreshKeywords,
        refreshKeywords,
    }

    @Jpf.Action
    protected Forward begin() throws Exception
    {
        requiresPermission(ACL.PERM_READ);
        FlowScript script = FlowScript.fromURL(getViewURLHelper(), getRequest());
        ScriptOverview overview = new ScriptOverview(getUser(), getContainer(), script);
        return includeView(new HomeTemplate(getViewContext(), new HtmlView(overview.toString()), getNavTrailConfig(script, null, Action.begin)));
    }

    protected Page getPage(String name) throws Exception
    {
        Page ret = (Page) getFlowPage(name);
        ret.setScript(getScript());
        return ret;
    }

    @Jpf.Action
    protected Forward chooseRunsToAnalyze(ChooseRunsToAnalyzeForm form) throws Exception
    {
        requiresPermission(ACL.PERM_INSERT);
        HttpView view = FormPage.getView(AnalysisScriptController.class, form, "chooseRunsToAnalyze.jsp");
        NavTrailConfig ntc = getNavTrailConfig(form.getProtocol(), "Choose runs", Action.chooseRunsToAnalyze);
        return includeView(new HomeTemplate(getViewContext(), view, ntc));
    }

    @Jpf.Action
    protected Forward analyzeSelectedRuns(ChooseRunsToAnalyzeForm form) throws Exception
    {
        requiresPermission(ACL.PERM_INSERT);
        int[] runIds = form.getSelectedRunIds();
        if (runIds.length == 0)
        {
            addError("Please select at least one run to analyze.");
            return chooseRunsToAnalyze(form);
        }
        String experimentLSID = form.getAnalysisLSID();
        if (experimentLSID == null)
        {
            return chooseAnalysisName(form);
        }
        FlowExperiment experiment = FlowExperiment.fromLSID(experimentLSID);
        String experimentName = form.ff_analysisName;
        if (experiment != null)
        {
            experimentName = experiment.getName();
        }
        FlowScript analysis = form.getProtocol();
        AnalyzeJob job = new AnalyzeJob(getViewBackgroundInfo(), experimentName, experimentLSID, FlowProtocol.ensureForContainer(getUser(), getContainer()), analysis, form.getProtocolStep(), runIds);
        if (form.getCompensationMatrixId() != 0)
        {
            job.setCompensationMatrix(FlowCompensationMatrix.fromCompId(form.getCompensationMatrixId()));
        }
        job.setCompensationExperimentLSID(form.getCompensationExperimentLSID());
        return executeScript(job, analysis);
    }

    protected Forward chooseAnalysisName(ChooseRunsToAnalyzeForm form) throws Exception
    {
        HttpView view = FormPage.getView(AnalysisScriptController.class, form, "chooseAnalysisName.jsp");
        NavTrailConfig ntc = getNavTrailConfig(form.getProtocol(), "Choose new analysis name", Action.chooseRunsToAnalyze);
        HomeTemplate template = new HomeTemplate(getViewContext(), view, ntc);
        template.getModel().setFocus("forms[0].ff_analysisName");
        return includeView(template);
    }

    protected Map<String, String> getNewPaths(ChooseRunsToUploadForm form) throws Exception
    {
        PipelineService service = PipelineService.get();
        PipeRoot root = service.findPipelineRoot(getContainer());
        if (root == null)
        {
            addError("The pipeline root is not set.");
            return Collections.EMPTY_MAP;
        }

        String displayPath;
        if (StringUtils.isEmpty(form.path))
        {
            displayPath = "this directory";
        }
        else
        {
            displayPath = "'" + form.path + "'";
        }
        File directory = StringUtils.isEmpty(form.path) ? root.getRootPath() : new File(root.getRootPath(), form.path);
        if (!root.isUnderRoot(directory))
        {
            addError("The path " + displayPath + " is invalid.");
            return Collections.EMPTY_MAP;
        }

        if (!directory.isDirectory())
        {
            addError(displayPath + " is not a directory.");
            return Collections.EMPTY_MAP;
        }
        List<File> files = new ArrayList();
        files.add(directory);
        File[] dirFiles = directory.listFiles();
        if (dirFiles != null)
        {
            files.addAll(Arrays.asList(dirFiles));
        }

        Set<String> usedPaths = new HashSet();
        for (FlowRun run : FlowRun.getRunsForContainer(getContainer(), FlowProtocolStep.keywords))
        {
            usedPaths.add(run.getExperimentRun().getFilePathRoot());
        }

        Map<String, String> ret = new TreeMap();
        boolean anyFCSDirectories = false;
        for (File file : files)
        {
            if (FlowAnalyzer.isFCSDirectory(file))
            {
                anyFCSDirectories = true;
                if (!usedPaths.contains(file.toString()))
                {
                    String relativePath = root.relativePath(file);
                    String displayName;
                    if (file.equals(directory))
                    {
                        displayName = "This Directory";
                    }
                    else
                    {
                        displayName = file.getName();
                    }
                    ret.put(relativePath, displayName);
                }
            }
        }
        if (ret.isEmpty())
        {
            if (anyFCSDirectories)
            {
                addError("All of the directories in " + displayPath + " have already been uploaded.");
            }
            else
            {
                addError("No FCS files were found in " + displayPath + " or its children.");
            }
        }
        return ret;

    }

    @Jpf.Action
    protected Forward chooseRunsToUpload(ChooseRunsToUploadForm form) throws Exception
    {
        requiresPermission(ACL.PERM_INSERT);
        PipeRoot root = PipelineService.get().findPipelineRoot(getContainer());
        root.requiresPermission(getContainer(), getUser(), ACL.PERM_INSERT);

        HttpView view = FormPage.getView(AnalysisScriptController.class, form, "chooseRunsToUpload.jsp");

        NavTrailConfig ntc = getNavTrailConfig(null, "Choose Runs To Upload", Action.chooseRunsToUpload);
        form.setNewPaths(getNewPaths(form));


        return includeView(new HomeTemplate(getViewContext(), view, ntc));
    }

    @Jpf.Action
    protected Forward uploadRuns(ChooseRunsToUploadForm form) throws Exception
    {
        requiresPermission(ACL.PERM_INSERT);
        PipeRoot root = PipelineService.get().findPipelineRoot(getContainer());
        root.requiresPermission(getContainer(), getUser(), ACL.PERM_INSERT);
        if (form.ff_path == null || form.ff_path.length == 0)
        {
            addError("You did not select any runs.");
            return chooseRunsToUpload(form);
        }
        List<File> paths = new ArrayList();
        List<String> skippedPaths = new ArrayList();
        for (String path : form.ff_path)
        {
            File file = root.resolvePath(path);
            if (file == null)
            {
                skippedPaths.add(path);
                continue;
            }

            paths.add(file);
        }

        ViewBackgroundInfo vbi = getViewBackgroundInfo();
        if (paths.size() > 0)
        {
            vbi = PipelineService.get().getJobBackgroundInfo(vbi, paths.get(0));
        }
        AddRunsJob job = new AddRunsJob(vbi, FlowProtocol.ensureForContainer(getUser(), vbi.getContainer()), paths);
        for (String path : skippedPaths)
        {
            job.addStatus("Skipping path '" + path + "' because it is invalid.");
        }
        return executeScript(job, null);
    }

    @Jpf.Action
    protected Forward showUploadRuns() throws Exception
    {
        requiresPermission(ACL.PERM_INSERT);
        ViewURLHelper forward = PipelineService.get().urlBrowse(getContainer());
        forward.addParameter("referer", FlowPipelineProvider.NAME);
        return new ViewForward(forward);
    }

    abstract static public class Page extends FlowPage
    {
        FlowScript _analysisScript;
        public void setScript(FlowScript script)
        {
            _analysisScript = script;
        }
        public FlowScript getScript()
        {
            return _analysisScript;
        }
    }
    protected Class<Action> getActionClass()
    {
        return Action.class;
    }

    protected boolean addError(String error)
    {
        PageFlowUtil.getActionErrors(getRequest(), true).add("main", new ActionError("Error", error));
        return true;
    }

    @Jpf.Action
    protected Forward uploadWorkspace() throws Exception
    {
        requiresPermission(ACL.PERM_INSERT);
        String path = getRequest().getParameter("path");
        PipeRoot root = PipelineService.get().findPipelineRoot(getContainer());
        File workspaceFile = root.resolvePath(path);
        URI dataFileURI = new File(workspaceFile.getParent(), "attributes.flowdata.xml").toURI();
        ExperimentService.Interface svc = ExperimentService.get();
        ExpRun run = svc.createExperimentRun(getContainer(), workspaceFile.getName());
        FlowProtocol flowProtocol = FlowProtocol.ensureForContainer(getUser(), getContainer());
        ExpProtocol protocol = flowProtocol.getProtocol();
        run.setProtocol(protocol);
        run.save(getUser());
        ExpData workspaceData = svc.createData(getContainer(), new DataType("Flow-Workspace"));
        workspaceData.setDataFileURI(workspaceFile.toURI());
        workspaceData.setName(workspaceFile.getName());
        workspaceData.save(getUser());

        FlowJoWorkspace workspace = FlowJoWorkspace.readWorkspace(new FileInputStream(workspaceFile), Collections.EMPTY_SET);
        ExpProtocolApplication startingInputs = run.addProtocolApplication(getUser(), null, ExpProtocol.ApplicationType.ExperimentRun);
        startingInputs.addDataInput(getUser(), workspaceData, InputRole.Workspace.toString(), InputRole.Workspace.getPropertyDescriptor(getContainer()));
        Map<FlowJoWorkspace.SampleInfo, ExpData> fcsFiles = new HashMap();
        for (FlowJoWorkspace.SampleInfo sample : workspace.getSamples())
        {
            ExpProtocolApplication paSample = run.addProtocolApplication(getUser(), FlowProtocolStep.keywords.getAction(protocol), ExpProtocol.ApplicationType.ProtocolApplication);
            paSample.addDataInput(getUser(), workspaceData, InputRole.Workspace.toString(), InputRole.Workspace.getPropertyDescriptor(getContainer()));
            ExpData fcsFile = svc.createData(getContainer(), FlowDataType.FCSFile);
            fcsFile.setName(sample.getLabel());
            fcsFile.setDataFileURI(dataFileURI);

            File dataFile = new File(workspaceFile.getParent(), sample.getLabel());
            fcsFile.setSourceApplication(paSample);
            fcsFile.save(getUser());
            fcsFiles.put(sample, fcsFile);
            AttributeSet attrs = new AttributeSet(ObjectType.fcsKeywords, dataFile.toURI());
            attrs.setKeywords(sample.getKeywords());
            attrs.save(getUser(), fcsFile);
        }
        for (Map.Entry<FlowJoWorkspace.SampleInfo, ExpData> entry : fcsFiles.entrySet())
        {
            AttributeSet results = workspace.getSampleAnalysisResults(entry.getKey());
            if (results != null)
            {
                ExpProtocolApplication paAnalysis = run.addProtocolApplication(getUser(),
                        FlowProtocolStep.analysis.getAction(protocol), ExpProtocol.ApplicationType.ProtocolApplication);
                paAnalysis.addDataInput(getUser(), entry.getValue(), InputRole.FCSFile.toString(), InputRole.FCSFile.getPropertyDescriptor(getContainer()));
                ExpData fcsAnalysis = svc.createData(getContainer(), FlowDataType.FCSAnalysis);
                fcsAnalysis.setName(flowProtocol.getFCSAnalysisName(new FlowFCSFile(entry.getValue())));
                fcsAnalysis.setSourceApplication(paAnalysis);
                fcsAnalysis.setDataFileURI(dataFileURI);
                fcsAnalysis.save(getUser());
                results.save(getUser(), fcsAnalysis);
            }
        }
        return new ViewForward(new FlowRun(run).urlShow());
    }
}
