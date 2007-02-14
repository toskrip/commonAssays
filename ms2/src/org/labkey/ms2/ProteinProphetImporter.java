package org.labkey.ms2;

import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.data.SqlDialect;
import org.labkey.api.data.Table;
import org.labkey.api.data.Container;
import org.labkey.api.util.NetworkDrive;
import org.labkey.ms2.protein.ProteinManager;
import org.labkey.common.tools.ProtXmlReader;
import org.labkey.common.tools.ProteinGroup;
import org.labkey.common.tools.SimpleXMLStreamReader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.ms2.pipeline.MS2PipelineManager;
import org.labkey.api.exp.XarContext;
import org.labkey.api.exp.ExperimentException;
import org.apache.log4j.Logger;

import javax.xml.stream.XMLStreamException;
import java.sql.*;
import java.io.*;
import java.util.*;

/**
 * User: jeckels
 * Date: Mar 7, 2006
 */
public class ProteinProphetImporter
{
    private static final Logger _log = Logger.getLogger(ProteinProphetImporter.class);

    private final File _file;
    private final String _experimentRunLSID;
    private final XarContext _context;
    private static final int STREAM_BUFFER_SIZE = 128 * 1024;

    public ProteinProphetImporter(File f, String experimentRunLSID, XarContext context)
    {
        _file = f;
        _experimentRunLSID = experimentRunLSID;
        _context = context;
    }

    public void importFile(PipelineJob job) throws SQLException, XMLStreamException, IOException, ExperimentException
    {
        long startTime = System.currentTimeMillis();
        job.getLogger().info("Starting to load ProteinProphet file " + _file.getPath());

        if (!shouldImportFile(job.getLogger(), job.getContainer()))
        {
            return;
        }

        MS2Run run = importRun(job);

        if (run == null)
        {
            job.getLogger().error("Failed to import MS2 run " + getPepXMLFileName());
            return;
        }

        SqlDialect dialect = MS2Manager.getSchema().getSqlDialect();
        int suffix = new Random().nextInt(1000000000);
        String peptidesTempTableName = dialect.getTempTablePrefix() +  "PeptideMembershipsTemp" + suffix;
        String proteinsTempTableName = dialect.getTempTablePrefix() +  "ProteinGroupMembershipsTemp" + suffix;

        Connection connection = MS2Manager.getSchema().getScope().beginTransaction();

        Statement stmt = null;
        PreparedStatement mergePeptideStmt = null;
        PreparedStatement mergeProteinStmt = null;
        PreparedStatement peptideStmt = null;
        PreparedStatement groupStmt = null;
        PreparedStatement proteinStmt = null;

        ProtXmlReader.ProteinGroupIterator iterator = null;
        boolean success = false;
        int proteinGroupIndex = 0;

        try
        {
            int fastaId = run.getFastaId();

            String createPeptidesTempTableSQL =
                "CREATE " +  dialect.getTempTableKeyword() +  " TABLE " + peptidesTempTableName + " ( " +
                    "\tTrimmedPeptide VARCHAR(200) NOT NULL,\n" +
                    "\tCharge INT NOT NULL,\n" +
                    "\tProteinGroupId INT NOT NULL,\n" +
                    "\tNSPAdjustedProbability REAL NOT NULL,\n" +
                    "\tWeight REAL NOT NULL,\n" +
                    "\tNondegenerateEvidence " + dialect.getBooleanDatatype() + " NOT NULL,\n" +
                    "\tEnzymaticTermini INT NOT NULL,\n" +
                    "\tSiblingPeptides REAL NOT NULL,\n" +
                    "\tSiblingPeptidesBin INT NOT NULL,\n" +
                    "\tInstances INT NOT NULL,\n" +
                    "\tContributingEvidence " + dialect.getBooleanDatatype() + " NOT NULL,\n" +
                    "\tCalcNeutralPepMass REAL NOT NULL" +
                    ")";

            stmt = connection.createStatement();
            stmt.execute(createPeptidesTempTableSQL);

            String createProteinsTempTableSQL =
                "CREATE " +  dialect.getTempTableKeyword() +  " TABLE " + proteinsTempTableName + " ( " +
                    "\tProteinGroupId INT NOT NULL,\n" +
                    "\tProbability REAL NOT NULL,\n" +
                    "\tLookupString VARCHAR(200)\n" +
                    ")";

            stmt = connection.createStatement();
            stmt.execute(createProteinsTempTableSQL);

            if (!NetworkDrive.exists(_file))
            {
                throw new FileNotFoundException(_file.toString());
            }
            ProtXmlReader reader = new ProtXmlReader(_file);

            peptideStmt = connection.prepareStatement("INSERT INTO " + peptidesTempTableName + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
            proteinStmt = connection.prepareStatement("INSERT INTO " + proteinsTempTableName + " (ProteinGroupId, Probability, LookupString) VALUES (?, ?, ?)");

            String groupString = "INSERT INTO " + MS2Manager.getTableInfoProteinGroups() + " (groupnumber, groupprobability, proteinprobability, indistinguishablecollectionid, proteinprophetfileid, uniquepeptidescount, totalnumberpeptides, pctspectrumids,  percentcoverage, errorrate) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            groupString = MS2Manager.getSqlDialect().appendSelectAutoIncrement(groupString, MS2Manager.getTableInfoProteinGroups(), "RowId");
            groupStmt = connection.prepareStatement(groupString);

            iterator = reader.iterator();

            ProteinProphetFile file = insertProteinProphetFile(job.getInfo(), run, iterator.getReader());

            while (iterator.hasNext())
            {
                proteinGroupIndex++;
                ProteinGroup group = iterator.next();
                float groupProbability = group.getProbability();
                int groupNumber = group.getGroupNumber();

                List<ProtXmlReader.Protein> proteins = group.getProteins();

                // collectionId 0 means it's the only collection in the group
                int collectionId = proteins.size() == 1 ? 0 : 1;
                for (ProtXmlReader.Protein protein : proteins)
                {
                    loadProtein(protein, groupNumber, groupProbability, collectionId++, file, job.getInfo(), groupStmt, peptideStmt, proteinStmt);
                }
                if (proteinGroupIndex % 50 == 0)
                {
                    // Don't leave too big of a transaction pending
                    // Commit directly on the connection so that we don't lose the underlying connection
                    peptideStmt.executeBatch();
                    connection.commit();
                }
                if (proteinGroupIndex % 10000 == 0)
                {
                    job.getLogger().info("Loaded " + proteinGroupIndex + " protein groups...");
                }
            }

            peptideStmt.executeBatch();

            // Move the peptide information of the temp table into the real table
            String mergePeptideSQL = "INSERT INTO " + MS2Manager.getTableInfoPeptideMemberships() + " (" +
                    "\tPeptideId, ProteinGroupId, NSPAdjustedProbability, Weight, NondegenerateEvidence,\n" +
                    "\tEnzymaticTermini, SiblingPeptides, SiblingPeptidesBin, Instances, ContributingEvidence, CalcNeutralPepMass ) \n" +
                    "\tSELECT p.RowId, t.ProteinGroupId, t.NSPAdjustedProbability, t.Weight, t.NondegenerateEvidence,\n" +
                    "\tt.EnzymaticTermini, t.SiblingPeptides, t.SiblingPeptidesBin, t.Instances, t.ContributingEvidence, t.CalcNeutralPepMass\n" +
                    "FROM " + MS2Manager.getTableInfoPeptides() + " p, " + peptidesTempTableName + " t\n" +
                    "WHERE p.TrimmedPeptide = t.TrimmedPeptide AND p.Charge = t.Charge AND p.Run = ?";

            mergePeptideStmt = connection.prepareStatement(mergePeptideSQL);
            mergePeptideStmt.setInt(1, run.getRun());
            mergePeptideStmt.executeUpdate();

            String mergeProteinSQL = "INSERT INTO " + MS2Manager.getTableInfoProteinGroupMemberships() + " (ProteinGroupId, Probability, SeqId) SELECT p.ProteinGroupId, p.Probability, s.SeqId FROM " + ProteinManager.getTableInfoFastaSequences() + " s, " + proteinsTempTableName + " p WHERE s.FastaId = ? AND s.LookupString = p.LookupString GROUP BY p.ProteinGroupId, p.Probability, s.SeqId";

            mergeProteinStmt = connection.prepareStatement(mergeProteinSQL);
            mergeProteinStmt.setInt(1, fastaId);
            mergeProteinStmt.executeUpdate();

            file.setUploadCompleted(true);
            Table.update(job.getInfo().getUser(), MS2Manager.getTableInfoProteinProphetFiles(), file, file.getRowId(), null);
            success = true;
            connection.commit();

            job.getLogger().info("ProteinProphet file import finished successfully, " + proteinGroupIndex + " protein groups loaded");
        }
        finally
        {
            if (iterator != null)
            {
                iterator.close();
            }
            if (connection != null)
            {
                try
                {
                    connection.rollback();
                }
                catch (SQLException e) { job.getLogger().error("Failed to rollback to clear any potential error state", e); }
            }
            if (stmt != null)
            {
                try
                {
                    stmt.execute("DROP TABLE " + peptidesTempTableName);
                }
                catch (SQLException e) { job.getLogger().error("Failed to drop temporary peptides table", e); }
                try
                {
                    stmt.execute("DROP TABLE " + proteinsTempTableName);
                }
                catch (SQLException e) { job.getLogger().error("Failed to drop temporary proteins table", e); }
                try { stmt.close(); } catch (SQLException e) {}
            }
            if (mergePeptideStmt != null) { try { mergePeptideStmt.close(); } catch (SQLException e) {} }
            if (mergeProteinStmt != null) { try { mergeProteinStmt.close(); } catch (SQLException e) {} }
            if (peptideStmt != null) { try { peptideStmt.close(); } catch (SQLException e) {} }
            if (groupStmt != null) { try { groupStmt.close(); } catch (SQLException e) {} }
            if (proteinStmt != null) { try { proteinStmt.close(); } catch (SQLException e) {} }
            MS2Manager.getSchema().getScope().closeConnection();

            if (!success)
            {
                job.getLogger().error("Failed when importing group " + proteinGroupIndex);
            }
        }
        long endTime = System.currentTimeMillis();
        job.getLogger().info("ProteinProphet import took " + ((endTime - startTime) / 1000) + " seconds.");
    }

    private boolean shouldImportFile(Logger logger, Container c) throws SQLException, IOException
    {
        ProteinProphetFile ppFile = MS2Manager.getProteinProphetFile(_file, c);
        if (ppFile != null)
        {
            if (ppFile.isUploadCompleted())
            {
                logger.info(_file.getPath() + " had already been uploaded successfully, not uploading again.");
                return false;
            }
            else
            {
                logger.info(_file.getPath() + " had already been partially uploaded, deleting the existing data.");
                MS2Manager.purgeProteinProphetFile(ppFile.getRowId());
            }
        }
        return true;
    }

    private ProteinProphetFile insertProteinProphetFile(ViewBackgroundInfo info, MS2Run run, SimpleXMLStreamReader parser)
            throws IOException, SQLException, XMLStreamException
    {
        ProteinProphetFile file = new ProteinProphetFile(parser);
        file.setFilePath(_file.getCanonicalPath());
        file.setRun(run.getRun());

        Table.insert(info.getUser(), MS2Manager.getTableInfoProteinProphetFiles(), file);
        return file;
    }

    private MS2Run importRun(PipelineJob job)
        throws IOException, XMLStreamException, SQLException, ExperimentException
    {
        String pepXMLFileName = getPepXMLFileName();
        // First, see if our usual XAR lookups can find it
        File pepXMLFile = _context.findFile(pepXMLFileName, _file.getParentFile());
        if (pepXMLFile == null)
        {
            // Second, try the file name in the XML in the current directory
            pepXMLFile = new File(_file.getParentFile(), new File(pepXMLFileName).getName());
            if (!NetworkDrive.exists(pepXMLFile))
            {
                // Third, try replacing the .pep-prot.xml on the file name with .pep.xml
                // and looking in the same directory
                if (MS2PipelineManager.isProtXMLFile(_file))
                {
                    String baseName = MS2PipelineManager.getBaseName(_file, 2);
                    pepXMLFile = MS2PipelineManager.getPepXMLFile(_file.getParentFile(), baseName);
                    if (!NetworkDrive.exists(pepXMLFile))
                    {
                        throw new FileNotFoundException(pepXMLFileName + " could not be found on disk.");
                    }
                }
            }
        }

        job.getLogger().info("Resolved referenced PepXML file to " + pepXMLFile.getPath());
        int runId = MS2Manager.addRun(job, pepXMLFile, false, _context);
        MS2Run run = MS2Manager.getRun(runId);
        if (_experimentRunLSID != null && run.getExperimentRunLSID() == null)
        {
            run.setExperimentRunLSID(_experimentRunLSID);
            MS2Manager.updateRun(run, job.getUser());
        }
        return run;
    }

    private void loadProtein(ProtXmlReader.Protein protein, int groupNumber, float groupProbability, int collectionId, ProteinProphetFile file, ViewBackgroundInfo info, PreparedStatement groupStmt, PreparedStatement peptideStatement, PreparedStatement proteinStmt)
            throws SQLException
    {
        int groupId = insertProteinGroup(protein, groupStmt, groupNumber, groupProbability, collectionId, file, info);

        insertPeptides(protein, peptideStatement, groupId);

        insertProteins(protein, proteinStmt, groupId);
    }

    private void insertProteins(ProtXmlReader.Protein protein, PreparedStatement proteinStmt, int groupId)
        throws SQLException
    {

        int proteinIndex = 1;
        proteinStmt.setInt(proteinIndex++, groupId);
        proteinStmt.setFloat(proteinIndex++, protein.getProbability());
        proteinStmt.setString(proteinIndex, protein.getProteinName());
        proteinStmt.execute();

        for (String indistinguishableProteinName : protein.getIndistinguishableProteinNames())
        {
            proteinStmt.setString(proteinIndex, indistinguishableProteinName);
            proteinStmt.execute();
        }
    }

    private void insertPeptides(ProtXmlReader.Protein protein, PreparedStatement peptideStatement, int groupId)
        throws SQLException
    {
        Set<String> insertedSequences = new HashSet<String>();

        for (ProtXmlReader.Peptide pep : protein.getPeptides())
        {
            if (insertedSequences.add(pep.getPeptideSequence()))
            {
                int index = 1;
                peptideStatement.setString(index++, pep.getPeptideSequence());
                peptideStatement.setInt(index++, pep.getCharge());
                peptideStatement.setLong(index++, groupId);
                peptideStatement.setFloat(index++, pep.getNspAdjustedProbability());
                peptideStatement.setFloat(index++, pep.getWeight());
                peptideStatement.setBoolean(index++, pep.isNondegenerateEvidence());
                peptideStatement.setInt(index++, pep.getEnzymaticTermini());
                peptideStatement.setFloat(index++, pep.getSiblingPeptides());
                peptideStatement.setInt(index++, pep.getSiblingPeptidesBin());
                peptideStatement.setInt(index++, pep.getInstances());
                peptideStatement.setBoolean(index++, pep.isContributingEvidence());
                peptideStatement.setFloat(index++, pep.getCalcNeutralPepMass());

                peptideStatement.addBatch();
            }
        }
    }

    private int insertProteinGroup(ProtXmlReader.Protein protein, PreparedStatement groupStmt, int groupNumber, float groupProbability, int collectionId, ProteinProphetFile file, ViewBackgroundInfo info)
        throws SQLException
    {
        ProtXmlReader.QuantitationRatio xpressRatio = protein.getQuantitationRatio();

        int groupIndex = 1;
        groupStmt.setInt(groupIndex++, groupNumber);
        groupStmt.setFloat(groupIndex++, groupProbability);
        groupStmt.setFloat(groupIndex++, protein.getProbability());
        groupStmt.setInt(groupIndex++, collectionId);
        groupStmt.setInt(groupIndex++, file.getRowId());
        groupStmt.setFloat(groupIndex++, protein.getPctSpectrumIds());
        groupStmt.setInt(groupIndex++, protein.getUniquePeptidesCount());
        groupStmt.setInt(groupIndex++, protein.getTotalNumberPeptides());
        groupStmt.setFloat(groupIndex++, protein.getPercentCoverage());
        Float errorRate = file.calculateErrorRate(groupProbability);
        if (errorRate == null)
        {
            groupStmt.setNull(groupIndex++, Types.FLOAT);
        }
        else
        {
            groupStmt.setFloat(groupIndex++, errorRate.floatValue());
        }

        groupStmt.execute();
        if (!groupStmt.getMoreResults())
        {
            throw new SQLException("Expected a result set with the new group's rowId");
        }
        ResultSet rs = null;
        int groupId;
        try
        {
            rs = groupStmt.getResultSet();
            if (!rs.next())
            {
                throw new SQLException("Expected a result set with the new group's rowId");
            }
            groupId = rs.getInt(1);
        }
        finally
        {
            if (rs != null) { try { rs.close(); } catch (SQLException e) {} }
        }

        if (xpressRatio != null)
        {
            xpressRatio.setProteinGroupId(groupId);
            Table.insert(info.getUser(), MS2Manager.getTableInfoProteinQuantitation(), xpressRatio);
        }
        return groupId;
    }


    private String getInClause(int length)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("?");
        for (int i = 1; i < length; i++)
        {
            sb.append(", ?");
        }
        return sb.toString();
    }

    private String getPepXMLFileName() throws FileNotFoundException, XMLStreamException
    {
        BeanXMLStreamReader parser = null;
        InputStream fIn = null;
        try
        {
            fIn = new BufferedInputStream(new FileInputStream(_file), STREAM_BUFFER_SIZE);
            parser = new BeanXMLStreamReader(fIn);
            if (parser.skipToStart("protein_summary_header"))
            {
                for (int i = 0; i < parser.getAttributeCount(); i++)
                {
                    if ("source_files".equals(parser.getAttributeLocalName(i)))
                    {
                        return parser.getAttributeValue(i);
                    }
                }
            }
        }
        finally
        {
            if (parser != null)
            {
                try
                {
                    parser.close();
                }
                catch (XMLStreamException e)
                {
                    _log.error(e);
                }
            }
            if (fIn != null)
            {
                try
                {
                    fIn.close();
                }
                catch (IOException e)
                {
                    _log.error(e);
                }
            }
        }
        throw new XMLStreamException("Could not find protein_summary_header element with attribute source_files");
    }

}
