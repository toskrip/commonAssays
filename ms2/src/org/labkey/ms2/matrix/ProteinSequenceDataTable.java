package org.labkey.ms2.matrix;

import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.exp.query.SamplesSchema;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.api.study.assay.AssayProtocolSchema;
import org.labkey.ms2.protein.query.ProteinUserSchema;
import org.labkey.ms2.query.MS2Schema;

import java.util.ArrayList;
import java.util.List;

public class ProteinSequenceDataTable extends FilteredTable<ProteinExpressionMatrixProtocolSchema>
{
    public ProteinSequenceDataTable(ProteinExpressionMatrixProtocolSchema schema)
    {
        super(ProteinExpressionMatrixProtocolSchema.getTableInfoSequenceData(), schema);

        setDetailsURL(AbstractTableInfo.LINK_DISABLER);
        setImportURL(AbstractTableInfo.LINK_DISABLER);

        addColumn(wrapColumn(getRealTable().getColumn("RowId"))).setHidden(true);

        ColumnInfo valueColumn = addColumn(wrapColumn(getRealTable().getColumn("Value")));
        valueColumn.setHidden(false);
        valueColumn.setLabel("Value");
        valueColumn.setFormat("0.000000");

        ColumnInfo seqIdColumn = addColumn(wrapColumn(getRealTable().getColumn("SeqId")));
        seqIdColumn.setHidden(false);
        seqIdColumn.setLabel("Seq Id");
        seqIdColumn.setFk(new QueryForeignKey(MS2Schema.SCHEMA_NAME, schema.getContainer(), null,
                schema.getUser(), ProteinUserSchema.ANNOTATION_TABLE_NAME, "RowId", "SeqId"));

        ColumnInfo sampleIdColumn = addColumn(wrapColumn(getRealTable().getColumn("SampleId")));
        sampleIdColumn.setHidden(false);
        sampleIdColumn.setLabel("Sample Id");

        // Allow for multiple sample sets, but assume we're displaying data from whichever is currently active.
        String activeSampleSet = ExperimentService.get().ensureActiveSampleSet(schema.getContainer()).getName();
        sampleIdColumn.setFk(new QueryForeignKey(SamplesSchema.SCHEMA_NAME, schema.getContainer(), null, schema.getUser(),
                activeSampleSet,"RowId","Name"));

        SQLFragment runSQL = new SQLFragment("(SELECT d.RunId FROM ");
        runSQL.append(ExperimentService.get().getTinfoData(), "d");
        runSQL.append(" WHERE d.RowId = ");
        runSQL.append(ExprColumn.STR_TABLE_ALIAS);
        runSQL.append(".DataId)");
        ColumnInfo runIdColumn = new ExprColumn(this, "Run", runSQL, JdbcType.INTEGER);
        addColumn(runIdColumn);
        runIdColumn.setFk(new QueryForeignKey(getUserSchema(), null, AssayProtocolSchema.RUNS_TABLE_NAME, "RowId", "Name"));

        ColumnInfo dataIdColumn = addColumn(wrapColumn(getRealTable().getColumn("DataId")));
        dataIdColumn.setHidden(false);
        dataIdColumn.setLabel("Data Id");
        dataIdColumn.setFk(new QueryForeignKey(ExpSchema.SCHEMA_NAME, schema.getContainer(), null, schema.getUser(), ExpSchema.TableType.Data.toString(),
                "RowId", "Name"));

        List<FieldKey> columns = new ArrayList<>(getDefaultVisibleColumns());
        columns.remove(dataIdColumn.getFieldKey());
        setDefaultVisibleColumns(columns);

        SQLFragment filter = new SQLFragment("DataId");
        filter.append(" IN (SELECT d.RowId FROM ");
        filter.append(ExperimentService.get().getTinfoData(), "d");
        filter.append(", ");
        filter.append(ExperimentService.get().getTinfoExperimentRun(), "r");
        filter.append(" WHERE d.RunId = r.RowId");
        if (schema.getProtocol() != null)
        {
            filter.append(" AND r.ProtocolLSID = ?");
            filter.add(schema.getProtocol().getLSID());
        }
        filter.append(") ");

        addCondition(filter, FieldKey.fromParts("DataId"));
    }

    @Override
    protected void applyContainerFilter(ContainerFilter filter)
    {
        // There isn't a container column directly on this table so do a special filter
        if (getContainer() != null)
        {
            FieldKey containerColumn = FieldKey.fromParts("Run", "Folder");
            clearConditions(containerColumn);
            addCondition(filter.getSQLFragment(getSchema(), new SQLFragment("(SELECT d.Container FROM exp.Data d WHERE d.RowId = DataId)"), getContainer()), containerColumn);
        }
    }
}