/*
 * Copyright (c) 2005-2008 LabKey Corporation
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

import org.apache.log4j.Logger;
import org.fhcrc.cpas.exp.xml.*;
import org.fhcrc.cpas.flow.script.xml.ScriptDef;
import org.fhcrc.cpas.flow.script.xml.ScriptDocument;
import org.labkey.api.data.Container;
import org.labkey.api.exp.api.*;
import org.labkey.api.util.GUID;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.flow.FlowSettings;
import org.labkey.flow.analysis.model.FlowException;
import org.labkey.flow.data.*;
import org.labkey.flow.persist.FlowManager;

import java.io.File;
import java.sql.SQLException;
import java.util.*;

abstract public class ScriptJob extends FlowJob
{
    private static Logger _log = getJobLogger(ScriptJob.class);

    public Logger getClassLogger()
    {
        return _log;
    }

    private Map<SampleKey, ExpMaterial> _sampleMap;
    private File _containerFolder;

    FlowScript _runAnalysisScript;
    FlowProtocolStep _step;
    FlowCompensationMatrix _compensationMatrix;
    String _experimentLSID;
    String _compensationExperimentLSID;
    String _experimentName;

    RunData _runData;
    List<String> _pendingRunLSIDs = new ArrayList();
    private final Map<FlowProtocolStep, List<String>> _processedRunLSIDs = new HashMap();
    FlowProtocol _protocol;

    private transient ScriptHandlerGroup _handlers;

    class RunData
    {
        public RunData(ExperimentRunType run)
        {
            _run = run;
        }
        ExperimentRunType _run;
        Map<LogType, StringBuffer> _logs = new EnumMap(LogType.class);
        Map<String, StartingInput> _runOutputs = new LinkedHashMap();
        Map<String, StartingInput> _startingDataInputs = new HashMap();
        Map<String, StartingInput> _startingMaterialInputs = new HashMap();
        public void logError(String lsid, String propertyURI, String message)
        {
            StringBuffer buf = _logs.get(LogType.error);
            if (buf == null)
            {
                buf = new StringBuffer();
                _logs.put(LogType.error, buf);
            }
            EnumMap<LogField, Object> values = new EnumMap(LogField.class);
            values.put(LogField.date, new Date());
            values.put(LogField.type, LogType.error);
            values.put(LogField.user, getUser());
            values.put(LogField.objectURI, lsid);
            values.put(LogField.propertyURI, propertyURI);
            values.put(LogField.message, message);
            FlowLog.append(buf, values);
        }
        public String getLSID()
        {
            return _run.getAbout();
        }
    }

    class ScriptHandlerGroup
    {
        private KeywordsHandler _runHandler;
        private CompensationCalculationHandler _compensationCalculationHandler;
        private AnalysisHandler _analysisHandler;

        public ScriptHandlerGroup()
        {
            try
            {
                ScriptDocument scriptDoc = null;
                if (_runAnalysisScript != null)
                {
                    scriptDoc = _runAnalysisScript.getAnalysisScriptDocument();
                }
                _runHandler = new KeywordsHandler(ScriptJob.this);
                if (scriptDoc != null)
                {
                    ScriptDef scriptDef = scriptDoc.getScript();
                    if (scriptDef.getCompensationCalculation() != null)
                    {
                        _compensationCalculationHandler = new CompensationCalculationHandler(ScriptJob.this,
                                scriptDef.getSettings(), scriptDef.getCompensationCalculation());
                    }
                    if (scriptDef.getAnalysis() != null)
                    {
                        _analysisHandler = new AnalysisHandler(ScriptJob.this,
                                scriptDef.getSettings(), scriptDef.getAnalysis());
                    }
                }
            }
            catch (Exception e)
            {
                warn("Failed to get script handlers for FlowScript:\n" +
                        (_runAnalysisScript == null ? "" : _runAnalysisScript.getAnalysisScript()), e);
            }
        }

        public KeywordsHandler getRunHandler()
        {
            return _runHandler;
        }

        public void setRunHandler(KeywordsHandler runHandler)
        {
            _runHandler = runHandler;
        }

        public CompensationCalculationHandler getCompensationCalculationHandler()
        {
            return _compensationCalculationHandler;
        }

        public void setCompensationCalculationHandler(CompensationCalculationHandler compensationCalculationHandler)
        {
            _compensationCalculationHandler = compensationCalculationHandler;
        }

        public AnalysisHandler getAnalysisHandler()
        {
            return _analysisHandler;
        }

        public void setAnalysisHandler(AnalysisHandler analysisHandler)
        {
            _analysisHandler = analysisHandler;
        }
    }

    class StartingInput
    {
        public StartingInput(String name, File file, InputRole role)
        {
            this.name = name;
            this.file = file;
            this.role = role;
        }
        String name;
        File file;
        InputRole role;
    }

    public ScriptJob(ViewBackgroundInfo info, String experimentName, String experimentLSID, FlowProtocol protocol, FlowScript script, FlowProtocolStep step) throws Exception
    {
        super(FlowPipelineProvider.NAME, info);
        _runAnalysisScript = script;
        _step = step;
        _experimentName = experimentName;
        _experimentLSID = experimentLSID;
        _protocol = protocol;
        _containerFolder = getWorkingFolder(getContainer());

        initStatus();
    }

    private ScriptHandlerGroup getHandlers()
    {
        if (_handlers == null)
            _handlers = new ScriptHandlerGroup();
        return _handlers;
    }

    public CompensationCalculationHandler getCompensationCalculationHandler()
    {
        return getHandlers().getCompensationCalculationHandler();
    }

    public AnalysisHandler getAnalysisHandler()
    {
        return getHandlers().getAnalysisHandler();
    }

    public KeywordsHandler getRunHandler()
    {
        return getHandlers().getRunHandler();
    }

    public void setCompensationExperimentLSID(String compensationExperimentLSID)
    {
        _compensationExperimentLSID = compensationExperimentLSID;
    }

    public void setCompensationMatrix(FlowCompensationMatrix comp)
    {
        _compensationMatrix = comp;
    }

    public FlowCompensationMatrix getCompensationMatrix()
    {
        return _compensationMatrix;
    }

    public FlowExperiment getExperiment()
    {
        return FlowExperiment.fromLSID(_experimentLSID);
    }

    public ActionURL urlData()
    {
        FlowExperiment experiment = getExperiment();
        if (experiment == null)
            return null;
        return experiment.urlShow();
    }

    public FlowRun[] findRuns(File path, FlowProtocolStep step) throws SQLException
    {
        FlowExperiment experiment = getExperiment();
        if (experiment == null)
        {
            return new FlowRun[0];
        }
        return experiment.findRun(path, step);
    }

    public FlowCompensationMatrix findCompensationMatrix(FlowRun run) throws SQLException
    {
        if (_compensationMatrix != null)
            return _compensationMatrix;

        FlowExperiment experiment = FlowExperiment.fromLSID(_compensationExperimentLSID != null ? _compensationExperimentLSID : _experimentLSID);
        if (experiment == null)
            return null;
        return experiment.findCompensationMatrix(run);
    }

    public FlowRun executeHandler(FlowRun srcRun, BaseHandler handler) throws Exception
    {
        ExperimentArchiveDocument doc = createExperimentArchive();
        ExperimentRunType runElement = addExperimentRun(doc.getExperimentArchive(), handler.getRunName(srcRun));
        File workingDirectory = createAnalysisDirectory(new File(srcRun.getPath()), handler._step);
        try
        {
            handler.processRun(srcRun, runElement, workingDirectory);
        }
        catch (FlowException e)
        {
            addError(null, null, e.getMessage());
        }
        finishExperimentRun(doc.getExperimentArchive(), runElement);
        importRuns(doc, new File(srcRun.getPath()), workingDirectory, handler._step);

        deleteAnalysisDirectory(workingDirectory);
        return FlowRun.fromLSID(runElement.getAbout());
    }

    public FlowCompensationMatrix ensureCompensationMatrix(FlowRun run) throws Exception
    {
        FlowCompensationMatrix ret = findCompensationMatrix(run);
        if (ret != null)
            return ret;
        if (getCompensationCalculationHandler() == null)
            return null;
        FlowRun compRun = executeHandler(run, getCompensationCalculationHandler());
        return compRun.getCompensationMatrix();
    }

    public String getDescription()
    {
        if (_runAnalysisScript != null)
            return _runAnalysisScript.getName();
        return "Upload";
    }

    public String getLog()
    {
        if (!getLogFile().exists())
        {
            return "No status";
        }
        return PageFlowUtil.getFileContentsAsString(getLogFile());
    }

    public Map<FlowProtocolStep, String[]> getProcessedRunLSIDs()
    {
        TreeMap<FlowProtocolStep, String[]> ret = new TreeMap<FlowProtocolStep, String[]>(new Comparator<FlowProtocolStep>() {
            public int compare(FlowProtocolStep o1, FlowProtocolStep o2)
            {
                return o1.getDefaultActionSequence() - o2.getDefaultActionSequence();
            }
        });
        synchronized(_processedRunLSIDs)
        {
            for (Map.Entry<FlowProtocolStep, List<String>> entry : _processedRunLSIDs.entrySet())
            {
                ret.put(entry.getKey(), entry.getValue().toArray(new String[0]));
            }
        }
        return ret;
    }

    public void addError(String lsid, String propertyURI, String message)
    {
        super.addError(lsid, propertyURI, message);
        if (_runData != null)
        {
            _runData.logError(lsid, propertyURI, message);
        }
    }

    protected boolean checkProcessPath(File path, FlowProtocolStep step) throws SQLException
    {
        FlowRun[] existing = findRuns(path, step);
        if (existing.length > 0)
        {
            addStatus("Skipping " + path.toString() + " because it already exists.");
            return false;
        }
        addStatus("Processing " + path.toString());
        return true;
    }

    public ExperimentArchiveDocument createExperimentArchive()
    {
        ExperimentArchiveDocument xarDoc = ExperimentArchiveDocument.Factory.newInstance();
        ExperimentArchiveType xar = xarDoc.addNewExperimentArchive();
        xar.addNewStartingInputDefinitions();
        if (_experimentLSID != null)
        {
            ExperimentType experiment = xar.addNewExperiment();
            experiment.setAbout(_experimentLSID);
            experiment.setName(_experimentName);
            experiment.setHypothesis("");
        }
        xar.addNewProtocolDefinitions();
        xar.addNewExperimentRuns();
        return xarDoc;
    }

    public ExperimentRunType addExperimentRun(ExperimentArchiveType xar, String name)
    {
        assert _runData == null;
        ExperimentRunType ret = xar.getExperimentRuns().addNewExperimentRun();
        ret.setAbout(ExperimentService.get().generateGuidLSID(getContainer(), ExpRun.class));
        ret.setProtocolLSID(getProtocol().getLSID());
        ret.setName(name);
        ret.addNewProtocolApplications();
        ret.addNewProperties();
        _runData = new RunData(ret);
        if (_runAnalysisScript != null)
        {
            addStartingInput(_runAnalysisScript, InputRole.AnalysisScript);
        }
        return ret;
    }

    public void finishExperimentRun(ExperimentArchiveType xar, ExperimentRunType run) throws SQLException
    {
        assert run == _runData._run;
        ProtocolApplicationBaseType appInput = insertProtocolApplication(run, 0);
        appInput.setName("Starting inputs");
        appInput.setProtocolLSID(getProtocol().getLSID());
        appInput.setActionSequence(0);
        appInput.setCpasType(ExpProtocol.ApplicationType.ExperimentRun.toString());
        for (Map.Entry<LogType, StringBuffer> logEntry : _runData._logs.entrySet())
        {
            StringBuffer buf = logEntry.getValue();
            if (buf == null || buf.length() == 0)
                continue;
            DataBaseType logData = appInput.getOutputDataObjects().addNewData();
            logData.setAbout(FlowDataObject.generateDataLSID(getContainer(), FlowDataType.Log));
            logData.setName(logEntry.getKey().toString());
            logData.addNewProperties();
            // TODO: FlowLog.PROP_Text(getPdCacheMap(), logData.getProperties(), buf.toString());
        }

        ExperimentArchiveType.StartingInputDefinitions defns = xar.getStartingInputDefinitions();
        InputOutputRefsType inputRefs = appInput.getInputRefs();
        for (Map.Entry<String, StartingInput> input : _runData._startingDataInputs.entrySet())
        {
            InputOutputRefsType.DataLSID dataLSID = inputRefs.addNewDataLSID();
            dataLSID.setStringValue(input.getKey());
            if (input.getValue().role != null)
            {
                dataLSID.setRoleName(input.getValue().role.toString());
            }
            DataBaseType dbt = defns.addNewData();
            dbt.setAbout(input.getKey());

            dbt.setName(input.getValue().name);
            if (input.getValue().file != null)
            {
                dbt.setDataFileUrl(input.getValue().file.toURI().toString());
            }
        }
        for (Map.Entry<String, StartingInput> input : _runData._startingMaterialInputs.entrySet())
        {
            inputRefs.addNewMaterialLSID().setStringValue(input.getKey());
        }
        ProtocolApplicationBaseType appOutput = addProtocolApplication(run);
        appOutput.setName("Mark run outputs");
        appOutput.setProtocolLSID(getProtocol().getLSID());
        appOutput.setActionSequence(FlowProtocolStep.markRunOutputs.getDefaultActionSequence());
        appOutput.setCpasType(ExpProtocol.ApplicationType.ExperimentRunOutput.toString());
        inputRefs = appOutput.getInputRefs();
        for (Map.Entry<String, StartingInput> entry : _runData._runOutputs.entrySet())
        {
            InputOutputRefsType.DataLSID dataLSID = inputRefs.addNewDataLSID();
            dataLSID.setStringValue(entry.getKey());
            if (entry.getValue().role != null)
            {
                dataLSID.setRoleName(entry.getValue().role.toString());
            }
        }
        _pendingRunLSIDs.add(_runData.getLSID());
        _runData = null;
    }

    public ProtocolApplicationBaseType addProtocolApplication(ExperimentRunType run)
    {
        return insertProtocolApplication(run, -1);
    }

    private ProtocolApplicationBaseType insertProtocolApplication(ExperimentRunType run, int index)
    {
        ProtocolApplicationBaseType app;
        if (index < 0)
        {
            app = run.getProtocolApplications().addNewProtocolApplication();
        }
        else
        {
            app = run.getProtocolApplications().insertNewProtocolApplication(index);
        }
        app.setAbout(ExperimentService.get().generateGuidLSID(getContainer(), ExpProtocolApplication.class));
        app.setActivityDate(new GregorianCalendar());
        app.addNewInputRefs();
        app.addNewOutputMaterials();
        app.addNewOutputDataObjects();
        return app;
    }

    public void addStartingInput(FlowDataObject data, InputRole role)
    {
        File file = null;
        if (data.getData().getDataFileURI() != null)
            file = new File(data.getData().getDataFileURI());
        String name = data.getName();
        addStartingInput(data.getLSID(), name, file, role);
    }

    public void addStartingMaterial(ExpMaterial material)
    {
        _runData._startingMaterialInputs.put(material.getLSID(), new StartingInput(material.getName(), null, null));
    }

    public void addInput(ProtocolApplicationBaseType app, FlowDataObject data, InputRole role)
    {
        addStartingInput(data, role);
        InputOutputRefsType.DataLSID dataLSID = app.getInputRefs().addNewDataLSID();
        dataLSID.setStringValue(data.getLSID());
        if (role != null)
        {
            dataLSID.setRoleName(role.toString());
        }
    }


    public void importRuns(ExperimentArchiveDocument xardoc, File root, File workingDirectory, FlowProtocolStep step) throws Exception
    {
        if (xardoc.getExperimentArchive().getExperimentRuns().getExperimentRunArray().length > 0)
        {
            addStatus("Inserting records into database");
            try
            {
                ScriptXarSource source = new ScriptXarSource(xardoc, root, workingDirectory, this);
                ExperimentService.get().importXar(source, this, true);
                FlowManager.get().updateFlowObjectCols(getContainer());
            }
            catch (Throwable t)
            {
                _log.error("Error loading XAR", t);
                _log.debug("Xar file contents:\n" + xardoc.toString());
                addError(null, null, "Error loading XAR: " + t.toString());
                throw UnexpectedException.wrap(t);
            }
        }

        addRunsLSIDs(step, _pendingRunLSIDs);
        _pendingRunLSIDs.clear();
    }


    private void addRunsLSIDs(FlowProtocolStep step, List<String> lsids)
    {
        synchronized(_processedRunLSIDs)
        {
            List<String> list = _processedRunLSIDs.get(step);
            if (list == null)
            {
                list = new ArrayList();
                _processedRunLSIDs.put(step, list);
            }
            list.addAll(lsids);
        }
    }

    public void addRunOutput(String lsid, InputRole role)
    {
        StartingInput input = new StartingInput(lsid, null, role);
        _runData._runOutputs.put(lsid, input);
    }

    public void addStartingInput(String lsid, String name, File file, InputRole role)
    {
        _runData._startingDataInputs.put(lsid, new StartingInput(name, file, role));
    }

    public boolean allowMultipleSimultaneousJobs()
    {
        return true;
    }

    private File getWorkingFolder(Container container) throws Exception
    {
        File dirRoot = FlowAnalyzer.getAnalysisDirectory();
        File dirFolder = new File(dirRoot, "Folder" + container.getRowId());
        if (!dirFolder.exists())
        {
            dirFolder.mkdir();
        }
        return dirFolder;
    }

    private void initStatus() throws Exception
    {
        String guid = GUID.makeGUID();
        File logFile = new File(_containerFolder, guid + ".flow.log");
        logFile.createNewFile();
        setLogFile(logFile);
    }

    public File createAnalysisDirectory(File runDirectory, FlowProtocolStep step) throws Exception
    {
        File dirFolder = getWorkingFolder(getContainer());
        File dirRun = new File(dirFolder, runDirectory.getName());
        if (!dirRun.exists())
        {
            dirRun.mkdir();
        }
        for (int i = 1; ; i ++)
        {
            File dirData = new File(dirRun, step.getLabel() + i);
            if (!dirData.exists())
            {
                dirData.mkdir();
                return dirData;
            }
        }
    }

    public void deleteAnalysisDirectory(File directory)
    {
        if (!FlowSettings.getDeleteFiles())
            return;
        if (hasErrors())
            return;
        try
        {
            File dirCompare = FlowAnalyzer.getAnalysisDirectory();
            if (!directory.toString().startsWith(dirCompare.toString()))
            {
                return;
            }
            for (File file : directory.listFiles())
            {
                file.delete();
            }
            directory.delete();
        }
        catch (Exception ioe)
        {
            _log.error("Error", ioe);
        }
    }

    synchronized public File decideFileName(File directory, String name, String extension)
    {
        File fileTry = new File(directory, name + "." + extension);
        if (!fileTry.exists())
            return fileTry;
        for (int i = 1; ; i++)
        {
            fileTry = new File(directory, name + i + "." + extension);
            if (!fileTry.exists())
                return fileTry;
        }
    }

    public FlowProtocol getProtocol()
    {
        return _protocol;
    }

    public Map<SampleKey, ExpMaterial> getSampleMap()
    {
        if (_sampleMap == null)
        {
            try
            {
                _sampleMap = _protocol.getSampleMap(getUser());
            }
            catch (SQLException e)
            {
                _log.error("Error", e);
                _sampleMap = Collections.EMPTY_MAP;
            }
        }
        return _sampleMap;
    }

}
