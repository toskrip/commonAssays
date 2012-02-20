/*
 * Copyright (c) 2008-2012 LabKey Corporation
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
package org.labkey.flow.controllers.executescript;

import org.labkey.flow.analysis.model.Workspace;
import org.labkey.flow.controllers.WorkspaceData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * User: kevink
 * Date: Jul 14, 2008 4:06:04 PM
 */
public class ImportAnalysisForm
{
    // unicode small comma (probably not in the gate name so is safer than comma as a separator char in LovCombo)
    public static final String PARAMATER_SEPARATOR = "\ufe50";
    // unicode fullwidth comma
    //public static final String NORMALIZATION_PARAMATER_SEPARATOR = "\uff0c";

    private int step = AnalysisScriptController.ImportAnalysisStep.SELECT_WORKSPACE.getNumber();
    private WorkspaceData workspace = new WorkspaceData();
    private int existingKeywordRunId;
    private String selectAnalysisEngine = "noEngine";

    // general analysis options and R normalization configuration
    private String importGroupNames = Workspace.ALL_SAMPLES;
    private Boolean rEngineNormalization = true;
    private String rEngineNormalizationReference = null;
    private String rEngineNormalizationParameters = null;
    private String rEngineNormalizationSubsets = null;

    private boolean createAnalysis;
    private String newAnalysisName;
    private int existingAnalysisId;
    private String runFilePathRoot;
    private boolean confirm;

    public int getStep()
    {
        return step;
    }

    public void setStep(int step)
    {
        this.step = step;
    }

    public AnalysisScriptController.ImportAnalysisStep getWizardStep()
    {
        return AnalysisScriptController.ImportAnalysisStep.fromNumber(step);
    }

    public void setWizardStep(AnalysisScriptController.ImportAnalysisStep step)
    {
        this.step = step.getNumber();
    }

    public WorkspaceData getWorkspace()
    {
        return workspace;
    }

    public int getExistingKeywordRunId()
    {
        return existingKeywordRunId;
    }

    public String getSelectAnalysisEngine()
    {
        return selectAnalysisEngine;
    }

    public void setSelectAnalysisEngine(String selectAnalysisEngine)
    {
        this.selectAnalysisEngine = selectAnalysisEngine;
    }

    public List<String> getImportGroupNameList()
    {
        return split(importGroupNames);
    }

    public String getImportGroupNames()
    {
        return importGroupNames;
    }

    public void setImportGroupNames(String importGroupNames)
    {
        this.importGroupNames = importGroupNames;
    }

    public Boolean isrEngineNormalization()
    {
        return rEngineNormalization;
    }

    public void setrEngineNormalization(Boolean rEngineNormalization)
    {
        this.rEngineNormalization = rEngineNormalization;
    }

    public String getrEngineNormalizationReference()
    {
        return rEngineNormalizationReference;
    }

    public void setrEngineNormalizationReference(String rEngineNormalizationReference)
    {
        this.rEngineNormalizationReference = rEngineNormalizationReference;
    }

    public List<String> getrEngineNormalizationParameterList()
    {
        return split(rEngineNormalizationParameters);
    }

    public String getrEngineNormalizationParameters()
    {
        return rEngineNormalizationParameters;
    }

    public void setrEngineNormalizationParameters(String rEngineNormalizationParameters)
    {
        this.rEngineNormalizationParameters = rEngineNormalizationParameters;
    }

    public List<String> getrEngineNormalizationSubsetList()
    {
        return split(rEngineNormalizationSubsets);
    }

    public String getrEngineNormalizationSubsets()
    {
        return rEngineNormalizationSubsets;
    }

    public void setrEngineNormalizationSubsets(String rEngineNormalizationParameters)
    {
        this.rEngineNormalizationSubsets = rEngineNormalizationParameters;
    }

    public void setExistingKeywordRunId(int existingKeywordRunId)
    {
        this.existingKeywordRunId = existingKeywordRunId;
    }

    public boolean isCreateAnalysis()
    {
        return createAnalysis;
    }

    public void setCreateAnalysis(boolean createAnalysis)
    {
        this.createAnalysis = createAnalysis;
    }

    public String getNewAnalysisName()
    {
        return newAnalysisName;
    }

    public void setNewAnalysisName(String newAnalysisName)
    {
        this.newAnalysisName = newAnalysisName;
    }

    public int getExistingAnalysisId()
    {
        return existingAnalysisId;
    }

    public void setExistingAnalysisId(int existingAnalysisId)
    {
        this.existingAnalysisId = existingAnalysisId;
    }

    public String getRunFilePathRoot()
    {
        return runFilePathRoot;
    }

    public void setRunFilePathRoot(String runFilePathRoot)
    {
        this.runFilePathRoot = runFilePathRoot;
    }

    public boolean isConfirm()
    {
        return confirm;
    }

    public void setConfirm(boolean confirm)
    {
        this.confirm = confirm;
    }


    private List<String> split(String list)
    {
        if (list == null)
            return Collections.emptyList();

        List<String> ret = new ArrayList<String>();
        for (String s : list.split(PARAMATER_SEPARATOR))
            ret.add(s.trim());
        return ret;
    }
}
