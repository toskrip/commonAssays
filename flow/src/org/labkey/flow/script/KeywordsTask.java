package org.labkey.flow.script;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.pipeline.AbstractTaskFactory;
import org.labkey.api.pipeline.AbstractTaskFactorySettings;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.pipeline.file.FileAnalysisJobSupport;
import org.labkey.api.util.FileType;
import org.labkey.flow.data.FlowProperty;
import org.labkey.flow.data.FlowProtocol;
import org.labkey.flow.data.FlowRun;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

public class KeywordsTask extends PipelineJob.Task<KeywordsTask.Factory>
{
    public KeywordsTask(Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    @NotNull
    @Override
    public RecordedActionSet run() throws PipelineJobException
    {
        PipelineJob job = getJob();
        FileAnalysisJobSupport support = job.getJobSupport(FileAnalysisJobSupport.class);

        try
        {
            FlowProtocol protocol = FlowProtocol.ensureForContainer(job.getUser(), job.getContainer());
            importFlowRuns(job, protocol, support.getInputFiles(), null);
        }
        catch(Exception e)
        {
            job.error("FCS Directory import failed: ", e);
        }

        return new RecordedActionSet();
    }

    public static List<FlowRun> importFlowRuns(PipelineJob job, FlowProtocol protocol, List<File> paths, Container targetStudyContainer) throws IOException, SQLException
    {
        PipeRoot pr = PipelineService.get().findPipelineRoot(job.getContainer());

        KeywordsJob keywordsJob = new KeywordsJob(job.getInfo(), protocol, paths, targetStudyContainer, pr);
        keywordsJob.setLogFile(job.getLogFile());
        keywordsJob.setLogLevel(job.getLogLevel());
        keywordsJob.setSubmitted();

        List<FlowRun> runs = keywordsJob.go();
        if (keywordsJob.hasErrors())
        {
            job.error("Failed to import keywords.");
            job.setStatus(PipelineJob.TaskStatus.error);
        }
        else
        {
            for (FlowRun run : runs)
            {
                String originalSourcePath = job.getParameters().get("OriginalSourcePath");
                if (null != originalSourcePath)
                {
                    job.info("Created keywords run '" + run.getName() + "' for path '" + run.getPath() + "' having original source path '" + originalSourcePath + "'");
                    run.setProperty(job.getUser(), FlowProperty.OriginalSourcePath.getPropertyDescriptor(), originalSourcePath);
                }
                else
                    job.info("Created keywords run '" + run.getName() + "' for path '" + run.getPath() + "'");
            }
        }

        return runs;
    }

    public static class Factory extends AbstractTaskFactory<AbstractTaskFactorySettings, Factory>
    {
        public Factory()
        {
            super(KeywordsTask.class);
        }

        public List<FileType> getInputTypes()
        {
            return Collections.emptyList();
        }

        public String getStatusName()
        {
            return "FCS KEYWORDS";
        }

        public List<String> getProtocolActionNames()
        {
            return Collections.emptyList();
        }

        public PipelineJob.Task createTask(PipelineJob job)
        {
            return new KeywordsTask(this, job);
        }

        public boolean isJobComplete(PipelineJob job)
        {
            return false;
        }
    }
}
