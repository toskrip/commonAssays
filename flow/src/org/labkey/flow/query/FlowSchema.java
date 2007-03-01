package org.labkey.flow.query;

import org.labkey.api.query.*;
import org.labkey.api.security.User;
import org.labkey.api.data.*;
import org.labkey.api.exp.api.*;
import org.labkey.flow.data.*;
import org.labkey.flow.data.FlowDataType;
import org.labkey.flow.util.PFUtil;
import org.labkey.flow.view.FlowQueryView;
import org.labkey.flow.persist.ObjectType;
import org.labkey.api.view.ViewURLHelper;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ViewForward;
import org.labkey.api.view.Portal;
import org.labkey.api.util.UnexpectedException;
import org.apache.beehive.netui.pageflow.Forward;

import javax.servlet.ServletException;
import java.util.*;
import java.sql.Types;

import org.labkey.flow.controllers.well.WellController;
import org.labkey.flow.controllers.FlowParam;
import org.labkey.flow.controllers.compensation.CompensationController;
import org.labkey.flow.controllers.executescript.AnalysisScriptController;
import org.labkey.flow.controllers.run.RunController;
import org.labkey.api.exp.api.SamplesSchema;
import org.labkey.api.exp.OntologyManager;


public class FlowSchema extends UserSchema
{
    static public final String SCHEMANAME = "flow";
    private FlowExperiment _experiment;
    private FlowRun _run;
    private FlowProtocol _protocol;

    public FlowSchema(User user, Container container)
    {
        super(SCHEMANAME, user, container, ExperimentService.get().getSchema());
        _protocol = FlowProtocol.getForContainer(_container);
    }

    public FlowSchema(ViewContext context) throws ServletException
    {
        this(context.getUser(), context.getContainer());
        setExperiment(FlowExperiment.fromURL(context.getViewURLHelper(), context.getRequest()));
        setRun(FlowRun.fromURL(context.getViewURLHelper()));
    }

    public FlowSchema detach()
    {
        if (_experiment == null && _run == null)
            return this;
        return new FlowSchema(_user, _container);
    }

    public TableInfo getTable(String name, String alias)
    {
        try
        {
            FlowTableType type = FlowTableType.valueOf(name);
            switch (type)
            {
                case FCSFiles:
                    return createFCSFileTable(alias);
                case FCSAnalyses:
                    return createFCSAnalysisTable(alias, FlowDataType.FCSAnalysis);
                case CompensationControls:
                    return createFCSAnalysisTable(alias, FlowDataType.CompensationControl);
                case Runs:
                    return createRunTable(alias, null);
                case CompensationMatrices:
                    return createCompensationMatrixTable(alias);
                case AnalysisScripts:
                    return createAnalysisScriptTable(alias);
                case Analyses:
                    return createAnalysesTable(alias);
            }
            return null;
        }
        catch (IllegalArgumentException iae)
        {
            return super.getTable(name, alias);
        }
    }

    public Set<String> getVisibleTableNames()
    {
        Set<String> ret = new HashSet();
        for (FlowTableType tt : FlowTableType.values())
        {
            if (!tt.isHidden())
                ret.add(tt.toString());
        }
        return ret;
    }

    public QueryDefinition getQueryDef(String name)
    {
        return QueryService.get().getQueryDef(getContainer(), getSchemaName(), name);
    }

    public QueryDefinition getQueryDef(FlowTableType qt)
    {
        return getQueryDef(qt.name());
    }

    public Set<String> getTableNames()
    {
        Set<String> ret = new LinkedHashSet();
        for (FlowTableType type : FlowTableType.values())
        {
            ret.add(type.toString());
        }
        return ret;
    }

    public Container getContainer()
    {
        return _container;
    }

    public User getUser()
    {
        return _user;
    }

    public void setExperiment(FlowExperiment experiment)
    {
        _experiment = experiment;
    }

    public void setRun(FlowRun run)
    {
        _run = run;
    }

    public FlowExperiment getExperiment()
    {
        return _experiment;
    }

    public FlowRun getRun()
    {
        return _run;
    }

    public ViewURLHelper urlFor(QueryAction action, FlowTableType type)
    {
        return urlFor(action, getQueryDefForTable(type.name()));
    }

    public ViewURLHelper urlFor(QueryAction action)
    {
        ViewURLHelper ret = super.urlFor(action);
        addParams(ret);
        return ret;
    }

    public ViewURLHelper urlFor(QueryAction action, QueryDefinition queryDef)
    {
        ViewURLHelper ret = super.urlFor(action, queryDef);
        addParams(ret);
        return ret;
    }

    public void addParams(ViewURLHelper url)
    {
        if (_run != null)
        {
            _run.addParams(url);
        }
        if (_experiment != null)
        {
            _experiment.addParams(url);
        }
    }

    public QueryView createView(ViewContext context, QuerySettings settings) throws ServletException
    {
        return new FlowQueryView(context, new FlowSchema(context), (FlowQuerySettings) settings);
    }

    public Forward delete(ViewURLHelper forward, QueryDefinition queryDef, String[] pks)
    {
        if (!(queryDef.getMainTable() instanceof ExpRunTable))
            throw new UnsupportedOperationException();
        try
        {
            ExperimentService.get().deleteExpRunsByRowIds(pks, getContainer());
        }
        catch (Exception e)
        {
            UnexpectedException.rethrow(e);
        }
        return new ViewForward(forward);
    }

    private SQLFragment sqlObjectTypeId(SQLFragment sqlDataId)
    {
        SQLFragment ret = new SQLFragment("(SELECT flow.Object.TypeId FROM flow.Object WHERE flow.Object.DataId = (");
        ret.append(sqlDataId);
        ret.append("))");
        return sqlDataId;
    }

    public ExpRunTable createRunTable(String alias, FlowDataType type)
    {
        ExpRunTable ret = ExperimentService.get().createRunTable(alias);

        if (_container != null)
        {
            ret.setContainer(_container);
        }
        if (_experiment != null)
        {
            ret.setExperiment(ExperimentService.get().getExpExperiment(_experiment.getExperimentId()));
        }
        ret.addColumn(ExpRunTable.Column.RowId);
        ret.setDetailsURL(new DetailsURL(PFUtil.urlFor(RunController.Action.showRun, _container), Collections.singletonMap(FlowParam.runId.toString(), ExpRunTable.Column.RowId.toString())));
        if (type == null || type == FlowDataType.FCSFile)
        {
            ret.addColumn(ExpRunTable.Column.Flag);
        }
        ret.addColumn(ExpRunTable.Column.Name);
        ColumnInfo colFilePathRoot = ret.addColumn(ExpRunTable.Column.FilePathRoot);
        colFilePathRoot.setIsHidden(true);
        ret.addColumn(ExpRunTable.Column.ProtocolStep);
        if (type != FlowDataType.FCSFile)
        {
            ColumnInfo colAnalysisScript;
            colAnalysisScript = ret.addDataInputColumn("AnalysisScript", InputRole.AnalysisScript.getPropertyDescriptor(getContainer()));
            colAnalysisScript.setFk(new LookupForeignKey(PFUtil.urlFor(AnalysisScriptController.Action.begin, getContainer()),
                    FlowParam.scriptId.toString(), "RowId", "Name"){
                public TableInfo getLookupTableInfo()
                {
                    return createAnalysisScriptTable("Lookup");
                }
            });
        }
        if (type != FlowDataType.CompensationMatrix && type != FlowDataType.FCSFile)
        {
            ColumnInfo colCompensationMatrix;
            colCompensationMatrix= ret.addDataInputColumn("CompensationMatrix", InputRole.CompensationMatrix.getPropertyDescriptor(getContainer()));
            colCompensationMatrix.setFk(new LookupForeignKey(null, (String) null, "RowId", "Name") {
                public TableInfo getLookupTableInfo()
                {
                    return detach().createCompensationMatrixTable("Lookup");
                }
            });
        }
        ret.addDataCountColumn("WellCount", InputRole.FCSFile.getPropertyDescriptor(getContainer()));
        ret.addColumn(ExpRunTable.Column.Created);
        ret.addColumn(ExpRunTable.Column.CreatedBy);
        return ret;
    }

    private SQLFragment dataIdCondition(String dataidName, ObjectType ... types)
    {
        SQLFragment ret = new SQLFragment("(SELECT flow.object.TypeId FROM flow.Object WHERE ");
        ret.append(dataidName + " = flow.object.DataId)");

        if (types.length == 1)
        {
            ret.append(" = " + types[0].getTypeId());
        }
        else
        {
            ret.append(" IN ( " + types[0].getTypeId());
            for (int i = 1; i < types.length; i ++)
            {
                ret.append(", " + types[i].getTypeId());
            }
            ret.append(" )");
        }
        return ret;
    }

    public ExpDataTable createDataTable(String alias, final FlowDataType type)
    {
        ExpDataTable ret = ExperimentService.get().createDataTable(alias);
        ret.setContainer(getContainer());
        ret.setDataType(type);
        ret.addCondition(dataIdCondition(ExpDataTable.COLUMN_ROWID, type.getObjectType()));
        ret.addColumn(ExpDataTable.Column.RowId);
        ret.addColumn(ExpDataTable.Column.Name);
        ret.addColumn(ExpDataTable.Column.Flag);
        ret.addColumn(ExpDataTable.Column.Created);
        ret.setTitleColumn("Name");
        ColumnInfo protocol = ret.addColumn(ExpDataTable.Column.Protocol);
        protocol.setIsHidden(true);

        ColumnInfo colRun = ret.addColumn(ExpDataTable.Column.Run);
        colRun.setFk(new LookupForeignKey(PFUtil.urlFor(RunController.Action.showRun, getContainer()), FlowParam.runId, "RowId", "Name")
        {
            public TableInfo getLookupTableInfo()
            {
                return FlowSchema.this.detach().createRunTable("run", type);
            }
        });
        if (_run != null)
        {
            ret.setRun(ExperimentService.get().getExpRun(_run.getRunId()));
        }
        return ret;
    }

    protected ColumnInfo addObjectIdColumn(ExpDataTable table, String name)
    {
        SQLFragment sql = new SQLFragment("(SELECT flow.Object.RowId FROM flow.Object WHERE flow.Object.DataId = ");
        sql.append(table.createColumn("RowId", ExpDataTable.Column.RowId).getValueSql(ExprColumn.STR_TABLE_ALIAS));
        sql.append(")");
        ColumnInfo ret = new ExprColumn(table, name, sql, Types.INTEGER);
        table.addColumn(ret);
        return ret;
    }

    static private class DeferredFCSFileVisibleColumns implements Iterable<FieldKey>
    {
        final private ExpDataTable _table;
        final private ColumnInfo _colKeyword;
        public DeferredFCSFileVisibleColumns(ExpDataTable table, ColumnInfo colKeyword)
        {
            _table = table;
            _colKeyword = colKeyword;
        }

        public Iterator<FieldKey> iterator()
        {
            List<FieldKey> ret = QueryService.get().getDefaultVisibleColumns(_table.getColumns());
            TableInfo lookup = _colKeyword.getFk().getLookupTableInfo();
            FieldKey keyKeyword = new FieldKey(null, _colKeyword.getName());
            if (lookup != null)
            {
                for (FieldKey key : lookup.getDefaultVisibleColumns())
                {
                    ret.add(FieldKey.fromParts(keyKeyword, key));
                }
            }
            return ret.iterator();
        }
    }

    public ExpDataTable createFCSFileTable(String alias)
    {
        final ExpDataTable ret = createDataTable(alias, FlowDataType.FCSFile);
        ret.setDetailsURL(new DetailsURL(PFUtil.urlFor(WellController.Action.showWell, getContainer()), Collections.singletonMap(FlowParam.wellId.toString(), ExpDataTable.Column.RowId.toString())));
        final ColumnInfo colKeyword = addObjectIdColumn(ret, "Keyword");
        FlowPropertySet fps = new FlowPropertySet(ret);
        colKeyword.setFk(new KeywordForeignKey(fps));
        colKeyword.setIsUnselectable(true);
        ret.addMethod("Keyword", new KeywordMethod(colKeyword));
        if (_protocol != null)
        {
            ExpSampleSet ss = _protocol.getSampleSet();
            ColumnInfo colMaterialInput = ret.addMaterialInputColumn("Sample", new SamplesSchema(getUser(), getContainer()), null, ss);
            if (ss == null)
            {
                colMaterialInput.setIsHidden(true);
            }
        }

        ret.setDefaultVisibleColumns(new DeferredFCSFileVisibleColumns(ret, colKeyword));
        return ret;
    }

    public ColumnInfo addStatisticColumn(ExpDataTable table, String columnAlias)
    {
        ColumnInfo colStatistic = addObjectIdColumn(table, columnAlias);
        colStatistic.setFk(new StatisticForeignKey(new FlowPropertySet(table)));
        colStatistic.setIsUnselectable(true);
        table.addMethod(columnAlias, new StatisticMethod(colStatistic));
        return colStatistic;
    }

    public ExpDataTable createFCSAnalysisTable(String alias, FlowDataType type)
    {
        ExpDataTable ret = createDataTable(alias, type);
        FlowPropertySet fps = new FlowPropertySet(ret);
        ret.setDetailsURL(new DetailsURL(PFUtil.urlFor(WellController.Action.showWell, getContainer()), Collections.singletonMap(FlowParam.wellId.toString(), ExpDataTable.Column.RowId.toString())));
        if (getExperiment() != null)
        {
            ret.setExperiment(ExperimentService.get().getExpExperiment(getExperiment().getLSID()));
        }
        addStatisticColumn(ret, "Statistic");
        ColumnInfo colGraph = addObjectIdColumn(ret, "Graph");
        colGraph.setFk(new GraphForeignKey(fps));
        colGraph.setIsUnselectable(true);
        ColumnInfo colFCSFile = ret.addDataInputColumn("FCSFile", InputRole.FCSFile.getPropertyDescriptor(getContainer()));
        colFCSFile.setFk(new LookupForeignKey(PFUtil.urlFor(WellController.Action.showWell, getContainer()),
                FlowParam.wellId.toString(),
                "RowId", "Name") {
                public TableInfo getLookupTableInfo()
                {
                    return FlowSchema.this.detach().createFCSFileTable("FCSFile");
                }
            });
        return ret;
    }

    public ExpDataTable createCompensationMatrixTable(String alias)
    {
        ExpDataTable ret = createDataTable(alias, FlowDataType.CompensationMatrix);
        ret.setDetailsURL(new DetailsURL(PFUtil.urlFor(CompensationController.Action.showCompensation, getContainer()), Collections.singletonMap(FlowParam.compId.toString(), ExpDataTable.Column.RowId.toString())));
        addStatisticColumn(ret, "Value");
        return ret;
    }

    public ExpDataTable createAnalysisScriptTable(String alias)
    {
        ExpDataTable ret = createDataTable(alias, FlowDataType.Script);
        ret.getColumn(ExpDataTable.Column.Run.toString()).setIsHidden(true);
        ret.setDetailsURL(new DetailsURL(PFUtil.urlFor(AnalysisScriptController.Action.begin, getContainer()), Collections.singletonMap(FlowParam.scriptId.toString(), "RowId")));
        return ret;
    }

    public ExpExperimentTable createAnalysesTable(String alias)
    {
        ExpExperimentTable ret = ExperimentService.get().createExperimentTable(alias);
        ret.setContainer(getContainer());
        FlowProtocol compensationProtocol = FlowProtocolStep.calculateCompensation.getForContainer(getContainer());
        FlowProtocol analysisProtocol = FlowProtocolStep.analysis.getForContainer(getContainer());
        ret.populate(new ExpSchema(getUser(), getContainer()));
        if (compensationProtocol != null)
        {
            ColumnInfo colCompensationCount = ret.createRunCountColumn("CompensationRunCount", null, compensationProtocol.getProtocol());
            ViewURLHelper detailsURL = FlowTableType.Runs.urlFor(getContainer(), "ProtocolStep", FlowProtocolStep.calculateCompensation.getName());
            colCompensationCount.setURL(detailsURL + "&" + FlowParam.experimentId.toString() + "=${RowId}");
            ret.addColumn(colCompensationCount);
        }
        if (analysisProtocol != null)
        {
            ColumnInfo colAnalysisRunCount = ret.createRunCountColumn("AnalysisRunCount", null, analysisProtocol.getProtocol());
            ViewURLHelper detailsURL = FlowTableType.Runs.urlFor(getContainer(), "ProtocolStep", FlowProtocolStep.analysis.getName());
            colAnalysisRunCount.setURL(detailsURL + "&" + FlowParam.experimentId.toString() + "=${RowId}");
            ret.addColumn(colAnalysisRunCount);
        }
        ret.setDetailsURL(new DetailsURL(FlowTableType.Runs.urlFor(getContainer(), (SimpleFilter) null, new Sort("ProtocolStep")),
                Collections.singletonMap(FlowParam.experimentId.toString(), ExpExperimentTable.Column.RowId.toString())));
        SQLFragment lsidCondition = new SQLFragment("LSID <> ");
        lsidCondition.appendStringLiteral(FlowExperiment.getExperimentRunExperimentLSID(getContainer()));
        ret.addCondition(lsidCondition);
        return ret;
    }

    public FlowQuerySettings getSettings(ViewURLHelper url, String dataRegionName)
    {
        return new FlowQuerySettings(url, dataRegionName);
    }

    public FlowQuerySettings getSettings(Portal.WebPart webPart, ViewURLHelper url)
    {
        return new FlowQuerySettings(webPart, url, getUser());
    }

    public QueryDefinition getQueryDefForTable(String name)
    {
        QueryDefinition ret = super.getQueryDefForTable(name);
        try
        {
            FlowTableType type = FlowTableType.valueOf(name);
            ret.setDescription(type.getDescription());
        }
        catch(IllegalArgumentException iae)
        {
            // ignore
        }
        return ret;
    }
}
