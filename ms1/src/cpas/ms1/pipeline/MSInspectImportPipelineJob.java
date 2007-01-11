package cpas.ms1.pipeline;

import org.fhcrc.cpas.pipeline.PipelineJob;
import org.fhcrc.cpas.pipeline.PipelineService;
import org.fhcrc.cpas.view.ViewBackgroundInfo;
import org.fhcrc.cpas.view.ViewURLHelper;
import org.fhcrc.cpas.data.Container;
import org.fhcrc.cpas.util.PathRelativizer;
import org.fhcrc.cpas.util.NetworkDrive;
import org.fhcrc.cpas.exp.ExperimentPipelineJob;
import org.fhcrc.cpas.exp.FileXarSource;
import org.apache.commons.lang.StringUtils;

import java.io.*;
import java.sql.SQLException;
import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;

/**
 * User: jeckels
 * Date: Nov 3, 2006
 * Handles the work of importing an msInspect analysis from
 * a .tsv file by writing a XAR and loading it. If the files on
 * the disk do not match the expected layout, the job will fail.
 */
public class MSInspectImportPipelineJob extends PipelineJob
{
    /** The .tsv file */
    private final File _featuresFile;

    public MSInspectImportPipelineJob(ViewBackgroundInfo info, File file)
    {
        super(MSInspectPipelineProvider.NAME, info);
        _featuresFile = file;

        setLogFile(new File(_featuresFile.getParentFile(), _featuresFile.getName() + ".log"));
    }

    public ViewURLHelper getStatusHref()
    {
        // No custom viewing for status while loading
        return null;
    }

    public String getDescription()
    {
        return _featuresFile.getName();
    }

    /**
     * Plug values into the XAR template to describe the location
     * of the files on disk.
     * @return XAR file describing the analysis that already happened
     * @throws IOException
     * @throws SQLException
     */
    private File writeExperiment() throws IOException, SQLException
    {
        // Grab the XAR template 
        String templateResource = "cpas/ms1/pipeline/templates/msInspectFeatureFinding.xml";

        InputStream in = getClass().getClassLoader().getResourceAsStream(templateResource);
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        StringBuilder sb = new StringBuilder();
        String line;
        try
        {
            while ((line = reader.readLine()) != null)
            {
                sb.append(line);
                sb.append("\n");
            }
        }
        finally
        {
            reader.close();
        }

        String xarXml = sb.toString();

        // Check out the file system for the expected hierarchy
        File resultsDir = _featuresFile.getParentFile();
        File msInspectDefFile = new File(resultsDir, "inspect.def");

        File protocolsDir = resultsDir.getParentFile();
        if (protocolsDir == null)
        {
            throw new IOException("Expected analysis files to be in a subdirectory ./inspect/<protocol_name>/ relative to the .mzXML file");
        }
        File analysisRootDir = protocolsDir.getParentFile();
        if (analysisRootDir == null)
        {
            throw new IOException("Expected analysis files to be in a subdirectory ./inspect/<protocol_name>/ relative to the .mzXML file");
        }

        String name = _featuresFile.getName().substring(0, _featuresFile.getName().length() - ".tsv".length());
        File mzXMLFile = new File(analysisRootDir, name + ".mzXML");
        if (!NetworkDrive.exists(mzXMLFile))
        {
            File xmlDir = new File(analysisRootDir, "xml");
            mzXMLFile = new File(xmlDir, name + ".mzXML");
        }

        String protocolName = resultsDir.getName();
        
        if (!NetworkDrive.exists(_featuresFile))
        {
            throw new IOException("Could not find features file on disk, expected it to be: " + _featuresFile);
        }
        if (!NetworkDrive.exists(msInspectDefFile))
        {
            throw new IOException("Could not find inspect.def   file on disk, expected it to be: " + msInspectDefFile);
        }
        if (!NetworkDrive.exists(mzXMLFile))
        {
            throw new IOException("Could not find mzXML file on disk, expected it to be: " + mzXMLFile);
        }

        // Build up the XML describing the input files
        StringBuilder mzxmlStartingInputsSB = new StringBuilder();
        StringBuilder instanceDetailsSB = new StringBuilder();
        
        mzxmlStartingInputsSB.append(getStartingInputDataSnippet(mzXMLFile, resultsDir));
        instanceDetailsSB.append(getInstanceDetailsSnippet(mzXMLFile, resultsDir, msInspectDefFile));

        String mzXMLPath = PathRelativizer.relativizePathUnix(resultsDir, mzXMLFile);

        Container c = _info.getContainer();
        PipelineService service = PipelineService.get();
        PipelineService.PipeRoot pr = service.findPipelineRoot(c);
        if (pr == null)
            throw new FileNotFoundException("Failed to find a pipeline root for " + c.getPath());
        File containerRoot = new File(pr.getUri(c));

        String searchName = "Run for " + name + " (" + protocolName + ")";

        // Drop the replacements into the template
        xarXml = StringUtils.replace(xarXml, "@@SEARCH_NAME@@", searchName);
        xarXml = StringUtils.replace(xarXml, "@@MZXML_STARTING_INPUTS@@", mzxmlStartingInputsSB.toString());
        xarXml = StringUtils.replace(xarXml, "@@MZXML_PATH@@", mzXMLPath);
        xarXml = StringUtils.replace(xarXml, "@@MSINSPECT_XML_FILE_PATH@@",
                PathRelativizer.relativizePathUnix(resultsDir, msInspectDefFile));

        String pepXMLFilePath = PathRelativizer.relativizePathUnix(resultsDir, _featuresFile);
        xarXml = StringUtils.replace(xarXml, "@@FEATURES_FILE_PATH@@",
                pepXMLFilePath);

        File uniquifierFile = resultsDir.getParentFile();
        String uniquifier = PathRelativizer.relativizePathUnix(containerRoot, uniquifierFile);

        xarXml = StringUtils.replace(xarXml, "@@RUN-UNIQUIFIER@@", uniquifier);

        // Write the XAR to disk so that it can be loaded
        File experimentXMLFile = new File(resultsDir, name + ".xar.xml");

        FileOutputStream fOut = new FileOutputStream(experimentXMLFile);
        PrintWriter writer = new PrintWriter(fOut);
        try
        {
            writer.write(xarXml);
        }
        finally
        {
            writer.close();
        }

        return experimentXMLFile;
    }

    public void run()
    {
        setStatus("CREATING EXPERIMENT");
        boolean completeStatus = false;
        try
        {
            File xarFile = writeExperiment();
            setStatus("LOADING EXPERIMENT");
            // Kick off an experiment load 
            String status = ExperimentPipelineJob.loadExperiment(this, new FileXarSource(xarFile));

            setStatus(status);
            completeStatus = true;
        }
        catch (SQLException e)
        {
            getLogger().error("msInspect load failed", e);
        }
        catch (IOException e)
        {
            getLogger().error("msInspect load failed", e);
        }
        catch (RuntimeException e)
        {
            getLogger().error("msInspect load failed", e);
        }
        finally
        {
            if (!completeStatus)
            {
                setStatus(PipelineJob.ERROR_STATUS);
            }
        }
    }

    private String getInstanceDetailsSnippet(File mzXMLFile, File analysisDir, File msInspectConfigFile) throws IOException
    {
        StringBuilder sb = new StringBuilder();
        sb.append("                      <exp:InstanceDetails>\n");
        sb.append("                        <exp:InstanceInputs>\n");
        sb.append("                          <exp:DataLSID DataFileUrl=\"");
        sb.append(PathRelativizer.relativizePathUnix(analysisDir, mzXMLFile));
        sb.append("\">${AutoFileLSID}</exp:DataLSID>\n");
        sb.append("                          <exp:DataLSID DataFileUrl=\"");
        sb.append(PathRelativizer.relativizePathUnix(analysisDir, msInspectConfigFile));
        sb.append("\">${AutoFileLSID}</exp:DataLSID>\n");
        sb.append("                        </exp:InstanceInputs>\n");
        sb.append("                      </exp:InstanceDetails>\n");
        return sb.toString();
    }

    private String getStartingInputDataSnippet(File f, File analysisDir) throws IOException
    {
        StringBuilder sb = new StringBuilder();
        sb.append("\t\t<exp:Data rdf:about=\"${AutoFileLSID}\">\n");
        sb.append("\t\t\t<exp:Name>");
        sb.append(f.getName());
        sb.append("</exp:Name>\n");
        sb.append("\t\t\t<exp:CpasType>Data</exp:CpasType>\n");
        sb.append("\t\t\t<exp:DataFileUrl>");
        sb.append(PathRelativizer.relativizePath(analysisDir, f));
        sb.append("</exp:DataFileUrl>\n");
        sb.append("\t\t</exp:Data>\n");
        return sb.toString();
    }
}
