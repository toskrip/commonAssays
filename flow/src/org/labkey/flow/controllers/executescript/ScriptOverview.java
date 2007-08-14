package org.labkey.flow.controllers.executescript;

import org.labkey.api.view.Overview;
import org.labkey.flow.data.FlowScript;
import org.labkey.flow.data.FlowProtocolStep;
import org.labkey.flow.data.FlowCompensationMatrix;
import org.labkey.flow.data.FlowRun;
import org.labkey.flow.controllers.editscript.ScriptController;
import org.labkey.flow.analysis.model.CompensationCalculation;
import org.labkey.flow.analysis.model.PopulationSet;
import org.labkey.flow.analysis.model.Population;
import org.labkey.flow.analysis.model.Analysis;
import org.labkey.api.security.User;
import org.labkey.api.security.ACL;
import org.labkey.api.data.Container;

import java.util.List;

public class ScriptOverview extends Overview
{
    FlowScript _script;
    int _runCount;
    boolean _canEdit;

    protected int countChildPopulations(PopulationSet popset)
    {
        int ret = 0;
        for (Population child : popset.getPopulations())
        {
            ret += 1 + countChildPopulations(child);
        }
        return ret;
    }
    public ScriptOverview(User user, Container container, FlowScript script)
    {
        super(user, container);
        _script = script;
        _runCount = _script.getRunCount();
        _canEdit = _runCount == 0 && hasPermission(ACL.PERM_UPDATE);
        setTitle("Analysis script '" + h(_script.getName()) + "'");
        StringBuilder explanatoryHTML = new StringBuilder();
        //explanatoryHTML.append("An analysis script tells " + FlowModule.getLongProductName() + " how to calculate the compensation matrix, what gates to apply, statistics to calculate, and graphs to draw.<br>");
        explanatoryHTML.append("Analysis scripts may have up to two sections in them.<br>");
        explanatoryHTML.append("The compensation calculation describes how to locate the compensation controls in each run, and which gates need to be applied to them.<br>");
        explanatoryHTML.append("The analysis section describes which gates in the analysis, as well as the statistics that need to be calculated, and the graphs that need to be drawn.<br>");
        setExplanatoryHTML(explanatoryHTML.toString());

        if (_runCount == 0)
        {
            setStatusHTML("This script has not yet been used to analyze data.");
        }
        else
        {
            setStatusHTML("This script has been used " + _runCount + " times.  For this reason, it cannot be edited.");
        }

        addStep(getCompensationCalculationStep());
        addStep(getAnalysisStep());
        addStep(getExecuteStep());
        if (hasPermission(ACL.PERM_UPDATE) && (hasStep(FlowProtocolStep.analysis) || hasStep(FlowProtocolStep.calculateCompensation)))
        {
            Action action = new Action("Make a copy of this analysis script", _script.urlFor(ScriptController.Action.copy));
            if (_runCount != 0)
            {
                action.setDescriptionHTML("This script cannot be edited anymore because it has been used.");
            }
            addAction(action);
        }
        if (_canEdit)
        {
            Action action = new Action("Delete this analysis script", _script.urlFor(ScriptController.Action.delete));
            addAction(action);
        }
        Action actionSettings = new Action("Edit Settings", _script.urlFor(ScriptController.Action.editSettings));
        actionSettings.setExplanatoryHTML("The script has settings that affect the way that graphs are drawn and statistics are calculated.");
        addAction(actionSettings);
        Action actionSource = new Action("View Source", _script.urlFor(ScriptController.Action.editScript));
        actionSource.setExplanatoryHTML("Advanced: Analysis scripts are XML documents that can be edited by hand");
        addAction(actionSource);
    }

    protected boolean hasStep(FlowProtocolStep step)
    {
        return _script.hasStep(step);
    }

    protected Step getCompensationCalculationStep()
    {
        Step.Status stepStatus;
        boolean hasStep = hasStep(FlowProtocolStep.calculateCompensation);
        if (hasStep)
        {
            stepStatus = Step.Status.completed;
        }
        else
        {
            if (_canEdit)
            {
                if (hasStep(FlowProtocolStep.analysis))
                {
                    stepStatus = Step.Status.optional;
                }
                else
                {
                    stepStatus = Step.Status.normal;
                }
            }
            else
            {
                stepStatus = Step.Status.disabled;
            }
        }
        Step ret = new Step("Define Compensation Calculation", stepStatus);
        ret.setExplanatoryHTML("The compensation calculation specifies the keywords that are used to identify the compensation wells, and specifies the gates that are to be applied.");
        if (hasStep)
        {
            try
            {
                Action action = new Action("Show compensation calculation.", _script.urlFor(ScriptController.Action.editCompensationCalculation));
                CompensationCalculation calc = (CompensationCalculation) _script.getCompensationCalcOrAnalysis(FlowProtocolStep.calculateCompensation);
                action.setDescriptionHTML("The compensation calculation involves " + calc.getChannelCount() + " channels and " + countChildPopulations(calc) + " gates.");
                ret.addAction(action);
            }
            catch (Exception e)
            {
                Action action = new Action("View Source", _script.urlFor(ScriptController.Action.editScript));
                action.setDescriptionHTML("An exception occurred: " + e);
                ret.addAction(action);
            }
        }
        else
        {
            ret.setStatusHTML("This script does not have a compensation calculation section.");
            if (_canEdit)
            {
                Action actionUpload = new Action("Upload a FlowJo workspace", _script.urlFor(ScriptController.Action.uploadCompensationCalculation));
                actionUpload.setExplanatoryHTML("You can upload a FlowJo workspace to define the compensation calculation.");
                ret.addAction(actionUpload);
                Action actionFromScratch = new Action("Define compensation calculation from scratch", _script.urlFor(ScriptController.Action.chooseCompensationRun));
                actionFromScratch.setExplanatoryHTML("You can also embark on the long process of using the online gate editor to define the compensation calculation");
                ret.addAction(actionFromScratch);
            }
        }

        return ret;
    }
    protected Step getAnalysisStep()
    {
        Step.Status stepStatus;
        boolean hasStep = hasStep(FlowProtocolStep.analysis);
        if (hasStep)
        {
            stepStatus = Step.Status.completed;
        }
        else
        {
            if (_canEdit)
            {
                if (hasStep(FlowProtocolStep.calculateCompensation))
                {
                    stepStatus = Step.Status.optional;
                }
                else
                {
                    stepStatus = Step.Status.normal;
                }
            }
            else
            {
                stepStatus = Step.Status.disabled;
            }
        }
        Step ret = new Step("Define Analysis", stepStatus);
        ret.setExplanatoryHTML("The analysis definition specifies which gates to apply, statistics to calculate, and graphs to draw.");
        if (hasStep)
        {
            try
            {
                Analysis analysis = (Analysis) _script.getCompensationCalcOrAnalysis(FlowProtocolStep.analysis);
                ret.setStatusHTML("This analysis has " + countChildPopulations(analysis) + " gates, " + analysis.getStatistics().size() + " statistics, and " +
                    analysis.getGraphs().size() + " graphs.");
            }
            catch (Exception e)
            {
                Action action = new Action("View Source", _script.urlFor(ScriptController.Action.editScript));
                action.setDescriptionHTML("An exception occurred: " + e);
                ret.addAction(action);
            }
        }

        if (_canEdit)
        {
            Action action = new Action("Upload a FlowJo workspace", _script.urlFor(ScriptController.Action.uploadAnalysis));
            if (hasStep)
            {
                action.setExplanatoryHTML("You can modify the gate definitions by uploading a FlowJo workspace");
            }
            else
            {
                action.setExplanatoryHTML("You can specify the gate definitions by uploading a FlowJo workspace");
            }
            ret.addAction(action);
        }
        Action actionEditGates = new Action(_canEdit ? "Edit gate definitions" :
                "View gate definitions", _script.urlFor(ScriptController.Action.gateEditor, FlowProtocolStep.analysis));
        if (hasStep)
        {
            actionEditGates.setExplanatoryHTML("You can use the online gate editor");
        }
        else
        {
            actionEditGates.setExplanatoryHTML("You can also define the analysis from scratch using the online gate editor.");
        }

        ret.addAction(actionEditGates);
        if (hasStep)
        {
            Action actionStats = new Action(_canEdit ? "Choose which statistics and graphs should be calculated" :
                    "View which statistics and graphs are to be calculated",  _script.urlFor(ScriptController.Action.editAnalysis));
            ret.addAction(actionStats);
            if (_canEdit)
            {
                Action actionRename = new Action("Rename populations", _script.urlFor(ScriptController.Action.editGateTree, FlowProtocolStep.analysis));
                ret.addAction(actionRename);
            }
        }
        return ret;
    }
    protected Step getExecuteStep()
    {
        boolean hasAnalysis = hasStep(FlowProtocolStep.analysis);
        boolean hasComp = hasStep(FlowProtocolStep.calculateCompensation);
        Step.Status stepStatus;
        FlowRun run = _script.getRun();

        if (run != null || !hasAnalysis && !hasComp)
        {
            stepStatus = Step.Status.disabled;
        }
        else
        {
            stepStatus = Step.Status.normal;
        }
        Step ret = new Step("Execute the analysis script", stepStatus);
        if (hasAnalysis && !hasComp && _script.requiresCompensationMatrix(FlowProtocolStep.analysis))
        {
            List<FlowCompensationMatrix> comps = FlowCompensationMatrix.getCompensationMatrices(getContainer());
            if (comps.size() == 0)
                ret.setStatusHTML("<b>Important: This analysis script requires a compensation matrix, but there are no compensation matrices in this folder.</b>");
        }
        if (_script.getRun() != null)
        {
            ret.setExplanatoryHTML("This script is part of the run '" + _script.getRun().getName() + "'.  It cannot be used to analyze another run, but you can make a copy of it.");
        }
        else if (hasAnalysis || hasComp)
        {
            Action action = new Action(hasAnalysis ? "Analyze some runs" : "Calculate some compensation matrices", _script.urlFor(AnalysisScriptController.Action.chooseRunsToAnalyze));
            ret.addAction(action);
        }
        else
        {
            ret.setExplanatoryHTML("This script must have either a compensation calculation or an analysis before it can be edited.");
        }
        return ret;
    }
}
