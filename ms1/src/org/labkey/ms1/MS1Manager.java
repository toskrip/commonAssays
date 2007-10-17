package org.labkey.ms1;

import org.labkey.api.data.DbSchema;
import org.labkey.api.data.Table;
import org.labkey.api.data.SchemaTableInfo;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.security.User;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.util.ResultSetUtil;

import java.sql.SQLException;
import java.sql.ResultSet;
import java.io.File;

public class MS1Manager
{
    private static MS1Manager _instance;
    public static final String SCHEMA_NAME = "ms1";
    public static final String TABLE_SCANS = "Scans";
    public static final String TABLE_CALIBRATION_PARAMS = "Calibrations";
    public static final String TABLE_PEAK_FAMILIES = "PeakFamilies";
    public static final String TABLE_PEAKS_TO_FAMILIES = "PeaksToFamilies";
    public static final String TABLE_PEAKS = "Peaks";
    public static final String TABLE_FEATURES = "Features";
    public static final String TABLE_FILES = "Files";
    public static final String TABLE_SOFTWARE = "Software";
    public static final String TABLE_SOFTWARE_PARAMS = "SoftwareParams";

    //constants for the file type bitmask
    public static final int FILETYPE_FEATURES = 1;
    public static final int FILETYPE_PEAKS = 2;

    private MS1Manager()
    {
        // prevent external construction with a private default constructor
    }

    public static synchronized MS1Manager get()
    {
        if (_instance == null)
            _instance = new MS1Manager();
        return _instance;
    }

    public String getSchemaName()
    {
        return SCHEMA_NAME;
    }
    public DbSchema getSchema()
    {
        return DbSchema.get(SCHEMA_NAME);
    }

    public SchemaTableInfo getTable(String tablename)
    {
        return getSchema().getTable(tablename);
    }

    /**
     * Returns the corresponding ScanId for a given Experiment runId and scan number.
     * @param runId The experiment run id
     * @param scan  The scan number
     * @return The corresponding ScanId from the ms1.Scans table, or null if no match is found
     * @throws SQLException Thrown if there is a database exception
     */
    public Integer getScanIdFromRunScan(int runId, int scan) throws SQLException
    {
        StringBuilder sql = new StringBuilder("SELECT ScanId FROM ").append(getSQLTableName(TABLE_SCANS));
        sql.append(" AS s INNER JOIN ");
        sql.append(getSQLTableName(TABLE_FILES));
        sql.append(" AS f ON (s.FileId=f.FileId) INNER JOIN exp.Data as d ON (f.ExpDataFileId=d.RowId)");
        sql.append(" WHERE d.RunId=").append(runId);
        sql.append(" AND s.Scan=").append(scan);
        
        return Table.executeSingleton(getSchema(), sql.toString(), null, Integer.class);
    }

    /**
     * Similar to getScanIdFromRunScan, but can get multiple scanIds for multiple scan numbers.
     * @param runId The experiment run id
     * @param scans The scan numbers
     * @return The corresponding scan ids
     * @throws SQLException thrown if there is a database exception
     */
    public Integer[] getScanIdsFromRunScans(int runId, Integer[] scans) throws SQLException
    {
        StringBuilder sql = new StringBuilder("SELECT ScanId FROM ").append(getSQLTableName(TABLE_SCANS));
        sql.append(" AS s INNER JOIN ");
        sql.append(getSQLTableName(TABLE_FILES));
        sql.append(" AS f ON (s.FileId=f.FileId) INNER JOIN exp.Data as d ON (f.ExpDataFileId=d.RowId)");
        sql.append(" WHERE d.RunId=").append(runId);
        sql.append(" AND s.Scan IN (");
        for(int idx = 0; idx < scans.length; ++idx)
        {
            if(scans[idx] == null)
                continue;

            if(idx > 0)
                sql.append(",");
            sql.append(scans[idx].intValue());
        }
        sql.append(") ORDER BY ScanId");

        return Table.executeArray(getSchema(), sql.toString(), null, Integer.class);
    }

    public Integer getRunIdFromFeature(int featureId) throws SQLException
    {
        StringBuilder sql = new StringBuilder("SELECT RunId FROM exp.Data AS d INNER JOIN ");
        sql.append(getSQLTableName(TABLE_FILES));
        sql.append(" AS f ON (d.RowId=f.ExpDataFileId) INNER JOIN ");
        sql.append(getSQLTableName(TABLE_FEATURES));
        sql.append(" AS fe ON (f.FileId=fe.FileId) WHERE fe.FeatureId=?");

        return Table.executeSingleton(getSchema(), sql.toString(), new Object[]{featureId}, Integer.class);
    }

    public boolean isPeakDataAvailable(int runId) throws SQLException
    {
        StringBuilder sql = new StringBuilder("SELECT count(*) FROM ");
        sql.append(getSQLTableName(TABLE_FILES));
        sql.append(" AS f INNER JOIN exp.Data AS d ON (f.ExpDataFileId=d.RowId)");
        sql.append(" WHERE d.RunId=").append(runId);
        sql.append(" AND f.Type=").append(FILETYPE_PEAKS);

        Integer num = Table.executeSingleton(getSchema(), sql.toString(), null, Integer.class);
        return (num != null && num.intValue() > 0);
    }

    public Integer getFileIdForRun(int runId, int fileType) throws SQLException
    {
        StringBuilder sql = new StringBuilder("SELECT FileId FROM ");
        sql.append(getSQLTableName(TABLE_FILES));
        sql.append(" AS f INNER JOIN exp.Data AS d ON (f.ExpDataFileId=d.RowId)");
        sql.append(" WHERE d.RunId=").append(runId);
        sql.append(" AND f.Type=").append(fileType);

        return Table.executeSingleton(getSchema(), sql.toString(), null, Integer.class);
    }

    public Integer getFileIdForScan(int scanId) throws SQLException
    {
        StringBuilder sql = new StringBuilder("SELECT FileId FROM ");
        sql.append(getSQLTableName(TABLE_SCANS));
        sql.append(" WHERE ScanId=").append(scanId);

        return Table.executeSingleton(getSchema(), sql.toString(), null, Integer.class);
    }

    public FeaturePeptideLink getFeaturePeptideLink(int featureId) throws SQLException
    {
        String sql = "SELECT fr.run AS MS2Run, pd.rowid AS PeptideId, pd.scan as Scan from ms2.Fractions as fr" +
                " inner join ms2.PeptidesData as pd on (fr.fraction=pd.fraction)" +
                " inner join (select MzXmlUrl, ms2scan, charge" +
                " from ms1.Files as fi inner join ms1.Features as fe on fi.FileId = fe.FileId" +
                " where FeatureId=?) as m1f on (pd.scan=m1f.ms2scan" +
                " and fr.mzxmlurl=m1f.MzXmlUrl)";

        FeaturePeptideLink link = null;
        ResultSet rs = null;

        try
        {
            rs = Table.executeQuery(getSchema(), sql, new Integer[]{featureId}, 1, false);

            if(rs.next())
            {
                //this query could match multiple peptides with different charges, but
                //we need to return the first one only
                link = new FeaturePeptideLink(rs.getLong("MS2Run"), rs.getInt("PeptideId"), rs.getInt("Scan"));
            }
        }
        finally
        {
            ResultSetUtil.close(rs);
        }
        return link;
    }

    public Feature getFeature(int featureId) throws SQLException
    {
        return Table.selectObject(getTable(TABLE_FEATURES), featureId, Feature.class);
    }

    public Scan getScan(int scanId) throws SQLException
    {
        return Table.selectObject(getTable(TABLE_SCANS), scanId, Scan.class);
    }

    public Software[] getSoftware(int fileId) throws SQLException
    {
        SimpleFilter fltr = new SimpleFilter("FileId", fileId);
        return Table.select(getTable(TABLE_SOFTWARE), Table.ALL_COLUMNS, fltr, null, Software.class);
    }

    public SoftwareParam[] getSoftwareParams(int softwareId) throws SQLException
    {
        SimpleFilter fltr = new SimpleFilter("SoftwareId", softwareId);
        return Table.select(getTable(TABLE_SOFTWARE_PARAMS), Table.ALL_COLUMNS, fltr, null, SoftwareParam.class);
    }

    public Table.TableResultSet getPeakData(int runId, int scan, double mzLow, double mzHigh) throws SQLException
    {
        StringBuilder sql = new StringBuilder("SELECT s.ScanId, s.Scan, s.RetentionTime, s.ObservationDuration, p.PeakId, p.MZ, p.Intensity, p.Area, p.Error, p.Frequency, p.Phase, p.Decay FROM ");
        sql.append(getSQLTableName(TABLE_PEAKS));
        sql.append(" AS p INNER JOIN ");
        sql.append(getSQLTableName(TABLE_SCANS));
        sql.append(" AS s ON (s.ScanId=p.ScanId) INNER JOIN ");
        sql.append(getSQLTableName(TABLE_FILES));
        sql.append(" AS f ON (s.FileId=f.FileId) INNER JOIN exp.Data AS d ON (f.expDataFileId=d.RowId) WHERE d.RunId=? AND f.Type=");
        sql.append(FILETYPE_PEAKS);
        sql.append(" AND s.Scan=? AND (p.MZ BETWEEN ? AND ?)");

        return Table.executeQuery(getSchema(), sql.toString(), new Object[]{runId, scan, mzLow, mzHigh});
    }

    public Table.TableResultSet getPeakData(int runId, double mzLow, double mzHigh, int scanFirst, int scanLast) throws SQLException
    {
        return getPeakData(runId, mzLow, mzHigh, scanFirst, scanLast, null);
    }

    public Table.TableResultSet getPeakData(int runId, double mzLow, double mzHigh, int scanFirst, int scanLast, String orderBy) throws SQLException
    {
        StringBuilder sql = new StringBuilder("SELECT s.ScanId, s.Scan, s.RetentionTime, s.ObservationDuration, p.PeakId, p.MZ, p.Intensity, p.Area, p.Error, p.Frequency, p.Phase, p.Decay FROM ");
        sql.append(getSQLTableName(TABLE_PEAKS));
        sql.append(" AS p INNER JOIN ");
        sql.append(getSQLTableName(TABLE_SCANS));
        sql.append(" AS s ON (s.ScanId=p.ScanId) INNER JOIN ");
        sql.append(getSQLTableName(TABLE_FILES));
        sql.append(" AS f ON (s.FileId=f.FileId) INNER JOIN exp.Data AS d ON (f.expDataFileId=d.RowId) WHERE d.RunId=? AND f.Type=");
        sql.append(FILETYPE_PEAKS);
        sql.append(" AND (p.MZ BETWEEN ? AND ?)");
        sql.append(" AND (s.Scan BETWEEN ? AND ?)");
        if(null != orderBy)
            sql.append(" ORDER BY ").append(orderBy);

        return Table.executeQuery(getSchema(), sql.toString(), new Object[]{runId, mzLow, mzHigh, scanFirst, scanLast});
    }

    public Table.TableResultSet getScanList(int runId, double mzLow, double mzHigh, int scanFirst, int scanLast) throws SQLException
    {
        StringBuilder sql = new StringBuilder("SELECT DISTINCT s.Scan FROM ");
        sql.append(getSQLTableName(TABLE_PEAKS));
        sql.append(" AS p INNER JOIN ");
        sql.append(getSQLTableName(TABLE_SCANS));
        sql.append(" AS s ON (s.ScanId=p.ScanId) INNER JOIN ");
        sql.append(getSQLTableName(TABLE_FILES));
        sql.append(" AS f ON (s.FileId=f.FileId) INNER JOIN exp.Data AS d ON (f.expDataFileId=d.RowId) WHERE d.RunId=? AND f.Type=");
        sql.append(FILETYPE_PEAKS);
        sql.append(" AND (p.MZ BETWEEN ? AND ?)");
        sql.append(" AND (s.Scan BETWEEN ? AND ?)");
        sql.append(" ORDER BY s.Scan");

        return Table.executeQuery(getSchema(), sql.toString(), new Object[]{runId, mzLow, mzHigh, scanFirst, scanLast});
    }

    public Integer[] getPrevNextScan(int runId, double mzLow, double mzHigh, int scanFirst, int scanLast, int scanCur) throws SQLException
    {
        StringBuilder sql = new StringBuilder(" FROM ");
        sql.append(getSQLTableName(TABLE_PEAKS));
        sql.append(" AS p INNER JOIN ");
        sql.append(getSQLTableName(TABLE_SCANS));
        sql.append(" AS s ON (s.ScanId=p.ScanId) INNER JOIN ");
        sql.append(getSQLTableName(TABLE_FILES));
        sql.append(" AS f ON (s.FileId=f.FileId) INNER JOIN exp.Data AS d ON (f.expDataFileId=d.RowId) WHERE d.RunId=? AND f.Type=");
        sql.append(FILETYPE_PEAKS);
        sql.append(" AND (p.MZ BETWEEN ? AND ?)");
        sql.append(" AND (s.Scan BETWEEN ? AND ?)");

        //find the max of those less than cur
        String sqlPrev = "SELECT MAX(s.Scan)" + sql.toString() + " AND s.Scan < ?";
        Integer prevScan = Table.executeSingleton(getSchema(), sqlPrev, new Object[]{runId, mzLow, mzHigh, scanFirst, scanLast, scanCur}, Integer.class);

        //find the min of those greater than cur
        String sqlNext = "SELECT MIN(s.Scan)" + sql.toString() + " AND s.Scan > ?";
        Integer nextScan = Table.executeSingleton(getSchema(), sqlNext, new Object[]{runId, mzLow, mzHigh, scanFirst, scanLast, scanCur}, Integer.class);

        return new Integer[]{prevScan, nextScan};
    }

    public void deleteFeaturesData(ExpData expData, User user) throws SQLException
    {
        int idExpData = expData.getRowId();

        StringBuilder sql = new StringBuilder("DELETE FROM ");
        sql.append(getSQLTableName(TABLE_FEATURES));
        sql.append(" WHERE FileId IN (");
        sql.append(genFileListSQL(idExpData));
        sql.append(");");

        sql.append("DELETE FROM ");
        sql.append(getSQLTableName(TABLE_SOFTWARE_PARAMS));
        sql.append(" WHERE SoftwareId IN (");
        sql.append(genSoftwareListSQL(idExpData));
        sql.append(");");

        sql.append("DELETE FROM ");
        sql.append(getSQLTableName(TABLE_SOFTWARE));
        sql.append(" WHERE FileId IN (");
        sql.append(genFileListSQL(idExpData));
        sql.append(");");

        sql.append("DELETE FROM ");
        sql.append(getSQLTableName(TABLE_FILES));
        sql.append(" WHERE ExpDataFileId=");
        sql.append(String.valueOf(idExpData));

        Table.execute(getSchema(), sql.toString(), null);
    }

    public void moveFileData(int oldExpDataFileID, int newExpDataFileID, User user) throws SQLException
    {
        Integer[] ids = {newExpDataFileID, oldExpDataFileID};
        Table.execute(getSchema(), "UPDATE " + SCHEMA_NAME + "." + TABLE_FILES + " SET ExpDataFileID=? WHERE ExpDataFileID=?", ids);
    }

    public void deletePeakData(int expDataFileID, User user) throws SQLException
    {
        StringBuilder sql = new StringBuilder("DELETE FROM ");
        sql.append(getSQLTableName(TABLE_PEAKS_TO_FAMILIES));
        sql.append(" WHERE PeakId IN (");
        sql.append(genPeakListSQL(expDataFileID));
        sql.append(");");

        sql.append("DELETE FROM ");
        sql.append(getSQLTableName(TABLE_PEAK_FAMILIES));
        sql.append(" WHERE ScanId IN (");
        sql.append(genScanListSQL(expDataFileID));
        sql.append(");");

        sql.append("DELETE FROM ");
        sql.append(getSQLTableName(TABLE_PEAKS));
        sql.append(" WHERE ScanId IN (");
        sql.append(genScanListSQL(expDataFileID));
        sql.append(");");

        sql.append("DELETE FROM ");
        sql.append(getSQLTableName(TABLE_CALIBRATION_PARAMS));
        sql.append(" WHERE ScanId IN (");
        sql.append(genScanListSQL(expDataFileID));
        sql.append(");");

        sql.append("DELETE FROM ");
        sql.append(getSQLTableName(TABLE_SCANS));
        sql.append(" WHERE FileId IN (");
        sql.append(genFileListSQL(expDataFileID));
        sql.append(");");

        sql.append("DELETE FROM ");
        sql.append(getSQLTableName(TABLE_SOFTWARE_PARAMS));
        sql.append(" WHERE SoftwareId IN (");
        sql.append(genSoftwareListSQL(expDataFileID));
        sql.append(");");

        sql.append("DELETE FROM ");
        sql.append(getSQLTableName(TABLE_SOFTWARE));
        sql.append(" WHERE FileId IN (");
        sql.append(genFileListSQL(expDataFileID));
        sql.append(");");

        sql.append("DELETE FROM ");
        sql.append(getSQLTableName(TABLE_FILES));
        sql.append(" WHERE ExpDataFileID=");
        sql.append(String.valueOf(expDataFileID));
        sql.append(";");

        Table.execute(getSchema(), sql.toString(), null);
    } //deletePeakData

    protected String genPeakListSQL(int expDataFileID)
    {
        StringBuilder sql = new StringBuilder("SELECT PeakId FROM ");
        sql.append(getSQLTableName(TABLE_PEAKS));
        sql.append(" WHERE ScanId IN (");
        sql.append(genScanListSQL(expDataFileID));
        sql.append(")");
        return sql.toString();
    }

    protected String genScanListSQL(int expDataFileID)
    {
        StringBuilder sql = new StringBuilder("SELECT ScanId FROM ");
        sql.append(getSQLTableName(TABLE_SCANS));
        sql.append(" WHERE FileId IN (");
        sql.append(genFileListSQL(expDataFileID));
        sql.append(")");
        return sql.toString();
    }

    protected String genFileListSQL(int expDataFileID)
    {
        StringBuilder sql = new StringBuilder("SELECT FileId FROM ");
        sql.append(getSQLTableName(TABLE_FILES));
        sql.append(" WHERE ExpDataFileId=");
        sql.append(String.valueOf(expDataFileID));
        return sql.toString();
    }

    protected String genSoftwareListSQL(int expDataFileID)
    {
        StringBuilder sql = new StringBuilder("SELECT SoftwareId FROM ");
        sql.append(getSQLTableName(TABLE_SOFTWARE));
        sql.append(" WHERE FileId IN (");
        sql.append(genFileListSQL(expDataFileID));
        sql.append(")");
        return sql.toString();
    }

    /**
     * Returns the fully-qualified table name (schema.table) for use in SQL statements
     * @param tableName The table name
     * @return Fully-qualified table name
     */
    public String getSQLTableName(String tableName)
    {
        return SCHEMA_NAME + "." + tableName;
    }

    /**
     * Returns true if this data file has already been imported into the experiment's container
     * @param dataFile  Data file to import
     * @param data      Experiment data object
     * @return          True if already loaded into the experiment's container, otherwise false
     * @throws SQLException Exception thrown from database layer
     */
    protected boolean isAlreadyImported(File dataFile, ExpData data) throws SQLException
    {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) AS existing FROM exp.Data as d INNER JOIN ");
        sql.append(getSQLTableName(TABLE_FILES));
        sql.append(" as f");
        sql.append(" ON (d.RowId = f.ExpDataFileId) WHERE DataFileUrl=? AND Container=?");

        Integer count = Table.executeSingleton(getSchema(), sql.toString(),
                                                new Object[]{dataFile.toURI().toString(), data.getContainer().getId()},
                                                Integer.class);
        return (null != count && count.intValue() != 0);
    } //isAlreadyImported()

    /**
     * Returns a string containing all errors from a SQLException, which may contain many messages
     * @param e The SQLException object
     * @return A string containing all the error messages
     */
    public String getAllErrors(SQLException e)
    {
        StringBuilder sb = new StringBuilder(e.toString());
        while(null != (e = e.getNextException()))
        {
            sb.append("; ");
            sb.append(e.toString());
        }
        return sb.toString();
    }

} //class MS1Manager