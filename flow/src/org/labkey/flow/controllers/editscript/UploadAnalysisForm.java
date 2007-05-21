package org.labkey.flow.controllers.editscript;

import org.apache.struts.upload.FormFile;
import org.apache.struts.action.ActionMapping;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.flow.analysis.model.FlowJoWorkspace;
import org.labkey.flow.analysis.model.Analysis;
import org.labkey.flow.analysis.model.StatisticSet;

import javax.servlet.http.HttpServletRequest;
import java.util.*;

public class UploadAnalysisForm extends EditScriptForm
{
    private static final Logger _log = Logger.getLogger(UploadAnalysisForm.class);

    public FormFile workspaceFile;
    public FlowJoWorkspace workspaceObject;
    public Set<StatisticSet> ff_statisticSet;

    public void reset(ActionMapping actionMapping, HttpServletRequest servletRequest)
    {
        super.reset(actionMapping, servletRequest);
        ff_statisticSet = EnumSet.noneOf(StatisticSet.class);
        try
        {
            Analysis analysis = (Analysis) getAnalysis();
            if (analysis == null || analysis.getStatistics().size() == 0)
            {
                EnumSet.of(StatisticSet.count, StatisticSet.frequencyOfParent);
            }
        }
        catch (Exception e)
        {
            _log.error("Error", e);
        }
    }

    public void setWorkspaceFile(FormFile file)
    {
        workspaceFile = file;
    }
    public void setWorkspaceObject(String object) throws Exception
    {
        workspaceObject = (FlowJoWorkspace) PageFlowUtil.decodeObject(object);
    }
    public String groupName;
    public void setGroupName(String groupName)
    {
        this.groupName = groupName;
    }

    public String sampleId;
    public void setSampleId(String sampleName)
    {
        this.sampleId = sampleName;
    }

    public void setFf_statisticSet(String[] values)
    {
        ff_statisticSet = EnumSet.noneOf(StatisticSet.class);
        for (String value : values)
        {
            if (StringUtils.isEmpty(value))
                continue;
            ff_statisticSet.add(StatisticSet.valueOf(value));
        }
    }
}
