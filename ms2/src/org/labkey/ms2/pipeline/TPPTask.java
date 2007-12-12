/*
 * Copyright (c) 2007 LabKey Software Foundation
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
package org.labkey.ms2.pipeline;

import org.apache.commons.lang.StringUtils;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.NetworkDrive;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * <code>TPPTask</code> PipelineJob task to run the TPP (xinteract) for further
 * analysis on a pepXML file generated by running a pepXML converter on a search
 * engine's raw output.  This task may run PeptideProphet, ProteinProphet,
 * Quantitation, and batch fractions into a single pepXML.
 */
public class TPPTask extends PipelineJob.Task
{
    private static final String EXT_PEP_XML = ".pep.xml";
    private static final String EXT_PEP_XSL = ".pep.xsl";
    private static final String EXT_PEP_SHTML = ".pep.shtml";
    private static final String EXT_PROT_XML = ".prot.xml";
    private static final String EXT_INTERMEDIATE_PROT_XML = ".pep-prot.xml";
    private static final String EXT_INTERMEDIATE_PROT_XSL = ".pep-prot.xsl";
    private static final String EXT_INTERMEDIATE_PROT_SHTML = ".pep-prot.shtml";

    public static File getPepXMLFile(File dirAnalysis, String baseName)
    {
        return new File(dirAnalysis, baseName + EXT_PEP_XML);
    }

    public static boolean isPepXMLFile(File file)
    {
        return file.getName().toLowerCase().endsWith(EXT_PEP_XML);
    }

    public static File getProtXMLFile(File dirAnalysis, String baseName)
    {
        return new File(dirAnalysis, baseName + EXT_PROT_XML);
    }

    public static boolean isProtXMLFile(File file)
    {
        String nameLc = file.getName().toLowerCase();
        return (nameLc.endsWith(EXT_PROT_XML) ||
                nameLc.endsWith(EXT_INTERMEDIATE_PROT_XML));
    }

    public static File getProtXMLIntermediatFile(File dirAnalysis, String baseName)
    {
        return new File(dirAnalysis, baseName + EXT_INTERMEDIATE_PROT_XML);
    }

    /**
     * Interface for support required from the PipelineJob to run this task,
     * beyond the base PipelineJob methods.
     */
    public interface JobSupport extends MS2PipelineJobSupport
    {
        /**
         * List of pepXML files to use as inputs to "xinteract".
         */
        File[] getInteractInputFiles();

        /**
         * True if PeptideProphet and ProteinProphet can be run on the input files.
         */
        boolean isProphetEnabled();

        /**
         * True if RefreshParser should run.
         */
        boolean isRefreshRequired();
    }

    public JobSupport getJobSupport()
    {
        return (JobSupport) getJob();
    }

    public String getStatusName()
    {
        return "ANALYSIS";
    }

    public boolean isComplete() throws IOException, SQLException
    {
        String baseName = getJobSupport().getOutputBasename();
        File dirAnalysis = getJobSupport().getAnalysisDirectory();

        if (!NetworkDrive.exists(getPepXMLFile(dirAnalysis, baseName)))
            return false;

        if (getJobSupport().isProphetEnabled() &&
                !NetworkDrive.exists(getProtXMLFile(dirAnalysis, baseName)))
            return false;

        return true;
    }

    public void run()
    {
        try
        {
            Map<String, String> params = getJob().getParameters();

            String baseName = getJobSupport().getOutputBasename();
            File dirAnalysis = getJobSupport().getAnalysisDirectory();
            File dirWork = MS2PipelineManager.createWorkingDirectory(dirAnalysis, baseName);

            File fileWorkPepXML = getPepXMLFile(dirWork, baseName);
            File fileWorkProtXML = null;
            if (getJobSupport().isProphetEnabled())
                fileWorkProtXML = getProtXMLIntermediatFile(dirWork, baseName);

            List<String> interactCmd = new ArrayList<String>();
            interactCmd.add("xinteract");

            if (!getJobSupport().isProphetEnabled())
            {
                interactCmd.add("-nP"); // no Prophet analysis
            }
            else
            {
                if ("yes".equalsIgnoreCase(params.get("pipeline prophet, accurate mass")))
                    interactCmd.add("-OptA");
                else
                    interactCmd.add("-Opt");
                interactCmd.add("-x20");    // 20 iterations extra for good measure.

                if (!getJobSupport().isRefreshRequired())
                    interactCmd.add("-nR");

                String paramMinProb = params.get("pipeline prophet, min probability");
                if (paramMinProb != null && paramMinProb.length() > 0)
                    interactCmd.add("-p" + paramMinProb);
            }

            String quantParam = getQuantitationCmd(params);
            if (quantParam != null)
                interactCmd.add(quantParam);

            interactCmd.add("-N" + fileWorkPepXML.getName());

            File[] inputFiles = getJobSupport().getInteractInputFiles();
            for (File fileInput : inputFiles)
                interactCmd.add(".." + File.separator + fileInput.getName());

            getJob().runSubProcess(new ProcessBuilder(interactCmd),
                    dirWork);

            MS2PipelineManager.moveWorkToParent(fileWorkPepXML);
            if (fileWorkProtXML != null)
                MS2PipelineManager.moveWorkFile(getProtXMLFile(dirAnalysis, baseName), fileWorkProtXML);

            // If no combined analysis is coming or this is the combined analysis, remove
            // the raw pepXML file(s).
            if (!getJobSupport().isFractions() || inputFiles.length > 1)
            {
                for (File fileInput : inputFiles)
                {
                    if (!fileInput.delete())
                        getJob().warn("Failed to delete intermediat file " + fileInput);
                }
            }

            // Deal with possible TPP outputs, if TPP was not XML_ONLY
            MS2PipelineManager.removeWorkFile(FileUtil.newFile(dirWork, baseName, EXT_PEP_XSL));
            MS2PipelineManager.removeWorkFile(FileUtil.newFile(dirWork, baseName, EXT_PEP_SHTML));
            MS2PipelineManager.removeWorkFile(FileUtil.newFile(dirWork, baseName, EXT_INTERMEDIATE_PROT_XSL));
            MS2PipelineManager.removeWorkFile(FileUtil.newFile(dirWork, baseName, EXT_INTERMEDIATE_PROT_SHTML));

            MS2PipelineManager.removeWorkingDirectory(dirWork);
        }
        catch (PipelineJob.RunProcessException erp)
        {
            // Handled in runSubProcess
        }
        catch (InterruptedException ei)
        {
            // Handled in runSubProcess
        }
        catch (IOException e)
        {
            getJob().error(e.getMessage(), e);
        }
    }

    public String getQuantitationCmd(Map<String, String> params)
    {
        String paramAlgorithm = params.get("pipeline quantitation, algorithm");
        if (paramAlgorithm == null)
            return null;
        if (!"q3".equalsIgnoreCase(paramAlgorithm) && !"xpress".equalsIgnoreCase(paramAlgorithm))
            return null;    // CONSIDER: error message.

        List<String> quantOpts = new ArrayList<String>();

        String paramQuant = params.get("pipeline quantitation, residue label mass");
        if (paramQuant != null)
            getLabelOptions(paramQuant, quantOpts);

        paramQuant = params.get("pipeline quantitation, mass tolerance");
        if (paramQuant != null)
            quantOpts.add("-m" + paramQuant);

        paramQuant = params.get("pipeline quantitation, heavy elutes before light");
        if (paramQuant != null)
            if("yes".equalsIgnoreCase(paramQuant))
                quantOpts.add("-b");

        paramQuant = params.get("pipeline quantitation, fix");
        if (paramQuant != null)
        {
            if ("heavy".equalsIgnoreCase(paramQuant))
                quantOpts.add("-H");
            else if ("light".equalsIgnoreCase(paramQuant))
                quantOpts.add("-L");
        }

        paramQuant = params.get("pipeline quantitation, fix elution reference");
        if (paramQuant != null)
        {
            String refFlag = "-f";
            if ("peak".equalsIgnoreCase(paramQuant))
                refFlag = "-F";
            paramQuant = params.get("pipeline quantitation, fix elution difference");
            if (paramQuant != null)
                quantOpts.add(refFlag + paramQuant);
        }

        paramQuant = params.get("pipeline quantitation, metabolic search type");
        if (paramQuant != null)
        {
            if ("normal".equalsIgnoreCase(paramQuant))
                quantOpts.add("-M");
            else if ("heavy".equalsIgnoreCase(paramQuant))
                quantOpts.add("-N");
        }

        String dirMzXMLPathName = getJobSupport().getDataDirectory().toString();
        // Strip trailing file separater, since on Windows this might be a \, which will
        // cause escaping difficulties.
        if (dirMzXMLPathName.endsWith(File.separator))
            dirMzXMLPathName = dirMzXMLPathName.substring(0, dirMzXMLPathName.length() - 1);
        quantOpts.add("\"-d" + dirMzXMLPathName + "\"");

        if ("xpress".equals(paramAlgorithm))
            return ("-X" + StringUtils.join(quantOpts.iterator(), ' '));

        String paramMinPP = params.get("pipeline quantitation, min peptide prophet");
        if (paramMinPP != null)
            quantOpts.add("--minPeptideProphet=" + paramMinPP);
        String paramMaxDelta = params.get("pipeline quantitation, max fractional delta mass");
        if (paramMaxDelta != null)
            quantOpts.add("--maxFracDeltaMass=" + paramMaxDelta);
        String paramCompatQ3 = params.get("pipeline quantitation, q3 compat");
        if ("yes".equalsIgnoreCase(paramCompatQ3))
            quantOpts.add("--compat");

        return ("-C1java -client -Xmx256M -jar "
                + "\"" /* + path to bin */ + "msInspect/viewerApp.jar" + "\""
                + " --q3 " + StringUtils.join(quantOpts.iterator(), ' ')
                + " -C2Q3ProteinRatioParser");
    }

    protected void getLabelOptions(String paramQuant, List<String> quantOpts)
    {
        String[] quantSpecs = paramQuant.split(",");
        for (String spec : quantSpecs)
        {
            String[] specVals = spec.split("@");
            if (specVals.length != 2)
                continue;
            String mass = specVals[0].trim();
            String aa = specVals[1].trim();
            quantOpts.add("-n" + aa + "," + mass);
        }
    }
}
