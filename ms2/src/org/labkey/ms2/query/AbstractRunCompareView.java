/*
 * Copyright (c) 2007-2010 LabKey Corporation
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

package org.labkey.ms2.query;

import org.labkey.api.data.*;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ExperimentUrls;
import org.labkey.api.gwt.client.model.GWTComparisonGroup;
import org.labkey.api.gwt.client.model.GWTComparisonMember;
import org.labkey.api.gwt.client.model.GWTComparisonResult;
import org.labkey.api.query.*;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.labkey.ms2.MS2Controller;
import org.labkey.ms2.MS2Run;
import org.labkey.ms2.RunListCache;
import org.labkey.ms2.RunListException;
import org.labkey.ms2.compare.CompareDataRegion;

import javax.servlet.ServletException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

/**
 * User: jeckels
 * Date: Jun 21, 2007
 */
public abstract class AbstractRunCompareView extends QueryView
{
    protected List<MS2Run> _runs;
    protected boolean _forExport;
    private SimpleFilter _runFilter = new SimpleFilter();
    private List<FieldKey> _columns;

    private Collection<String> _errors = new ArrayList<String>();
    protected final String _peptideViewName;

    public AbstractRunCompareView(ViewContext context, int runListIndex, boolean forExport, String tableName, String peptideViewName) throws ServletException
    {
        super(new MS2Schema(context.getUser(), context.getContainer()), createSettings(context, tableName));
        _peptideViewName = peptideViewName;

        _viewContext.setActionURL(context.getActionURL());

        try
        {
            _runs = RunListCache.getCachedRuns(runListIndex, false, context);
        }
        catch (RunListException e)
        {
            _runs = null;
            _errors = e.getMessages();
        }

        if (_runs != null)
        {
            for (MS2Run run : _runs)
            {
                Container c = run.getContainer();
                if (c == null || !c.hasPermission(getUser(), ReadPermission.class))
                {
                    HttpView.throwUnauthorized();
                }
            }

            getSchema().setRuns(_runs);

            _forExport = forExport;

            setButtonBarPosition(DataRegion.ButtonBarPosition.BOTTOM);
        }
        // ExcelWebQueries won't be part of the same HTTP session so we won't have access to the run list anymore
        setAllowExportExternalQuery(false);
    }

    public List<MS2Run> getRuns()
    {
        return _runs;
    }
    
    public Collection<String> getErrors()
    {
        return _errors;
    }

    private static QuerySettings createSettings(ViewContext context, String tableName)
    {
        QuerySettings settings = new QuerySettings(context, "Compare");
        settings.setSchemaName(MS2Schema.SCHEMA_NAME);
        settings.setQueryName(tableName);
        settings.setAllowChooseQuery(false);
        return settings;
    }

    public MS2Schema getSchema()
    {
        return (MS2Schema)super.getSchema();
    }

    protected abstract String getGroupingColumnName();

    public GWTComparisonResult createCompareResult()
            throws SQLException, IOException, ServletException
    {
        List<FieldKey> cols = new ArrayList<FieldKey>();
        cols.add(FieldKey.fromParts(getGroupingColumnName()));
        cols.add(FieldKey.fromParts("Run", "RowId"));
        setColumns(cols);

        StringBuilder sb = new StringBuilder();

        TSVGridWriter tsvWriter = getTsvWriter();
        try
        {
            tsvWriter.setCaptionRowVisible(false);
            tsvWriter.write(sb);

            StringTokenizer lines = new StringTokenizer(sb.toString(), "\n");
            int proteinCount = lines.countTokens();
            GWTComparisonMember[] gwtRuns = new GWTComparisonMember[_runs.size()];
            Map<Integer, GWTComparisonGroup> runGroups = new HashMap<Integer, GWTComparisonGroup>();
            boolean[][] hits = new boolean[_runs.size()][];
            for (int i = 0; i < _runs.size(); i++)
            {
                hits[i] = new boolean[proteinCount];
            }

            int index = 0;
            while (lines.hasMoreTokens())
            {
                String line = lines.nextToken();
                String[] values = line.split("\\t");
                for (int i = 0; i < _runs.size() && i + 1 < values.length ; i++)
                {
                    hits[i][index] = !"".equals(values[i + 1].trim());
                }
                index++;
            }
            
            for (int runIndex = 0; runIndex < _runs.size(); runIndex++)
            {
                MS2Run run = _runs.get(runIndex);
                ActionURL runURL = MS2Controller.getShowRunURL(run.getContainer(), run.getRun());
                String lsid = run.getExperimentRunLSID();
                ExpRun expRun = null;
                if (lsid != null)
                {
                    expRun = ExperimentService.get().getExpRun(lsid);
                }
                GWTComparisonMember gwtRun = new GWTComparisonMember(run.getDescription(), hits[runIndex]);
                gwtRun.setUrl(runURL.toString());
                if (expRun != null)
                {
                    ExpExperiment[] experiments = expRun.getExperiments();
                    for (ExpExperiment experiment : experiments)
                    {
                        GWTComparisonGroup comparisonGroup = runGroups.get(experiment.getRowId());
                        if (comparisonGroup == null)
                        {
                            comparisonGroup = new GWTComparisonGroup();
                            comparisonGroup.setURL(PageFlowUtil.urlProvider(ExperimentUrls.class).getExperimentDetailsURL(experiment.getContainer(), experiment).toString());
                            comparisonGroup.setName(experiment.getName());
                            runGroups.put(experiment.getRowId(), comparisonGroup);
                        }
                        comparisonGroup.addMember(gwtRun);
                    }
                }
                gwtRuns[runIndex] = gwtRun; 
            }

            return new GWTComparisonResult(gwtRuns, runGroups.values().toArray(new GWTComparisonGroup[runGroups.size()]), proteinCount, "Runs");
        }
        finally
        {
            tsvWriter.close();
        }
    }
    
    public DataView createDataView()
    {
        DataView result = super.createDataView();
        Sort sort = result.getRenderContext().getBaseSort();
        if (sort == null)
        {
            sort = new Sort();
        }
        sort.insertSortColumn("-RunCount", false, sort.getSortList().size());
        sort.insertSortColumn("-Pattern", false, sort.getSortList().size());
        result.getRenderContext().setBaseSort(sort);
        result.getRenderContext().setViewContext(getViewContext());
        SimpleFilter filter = new SimpleFilter(result.getRenderContext().getBaseFilter());

        for (SimpleFilter.FilterClause clause : new ArrayList<SimpleFilter.FilterClause>(filter.getClauses()))
        {
            for (String colName : clause.getColumnNames())
            {
                if (colName.startsWith("Run/"))
                {
                    SimpleFilter filterToRemove = new SimpleFilter();
                    filterToRemove.addClause(clause);
                    String urlParam = filterToRemove.toQueryString(getSettings().getDataRegionName());
                    if (urlParam != null && urlParam.indexOf('=') != -1)
                    {
                        filter.deleteConditions(colName);

                        SimpleFilter.OrClause orClause = new SimpleFilter.OrClause();
                        for (MS2Run run : _runs)
                        {
                            String newParam = urlParam.replace("Run%2F", "Run" + run.getRun() + "%2F");
                            ActionURL newURL = result.getRenderContext().getViewContext().cloneActionURL();
                            newURL.deleteParameters();
                            newURL = new ActionURL(newURL + newParam);
                            SimpleFilter newFilter = new SimpleFilter(newURL, getSettings().getDataRegionName());
                            for (SimpleFilter.FilterClause newClause : newFilter.getClauses())
                            {
                                orClause.addClause(newClause);
                            }
                        }
                        _runFilter.addClause(orClause);
                    }
                }
            }
        }

        filter.addAllClauses(_runFilter);

        result.getRenderContext().setBaseFilter(filter);
        return result;
    }

    protected abstract String getGroupHeader();

    public void setColumns(List<FieldKey> columns)
    {
        _columns = columns;
    }

    @Override
    protected DataRegion createDataRegion()
    {
        CompareDataRegion rgn = new CompareDataRegion(null, getGroupHeader());
        configureDataRegion(rgn);
        configureDataRegion(rgn);
        List<DisplayColumn> displayColumns = getDisplayColumns();
        int offset = 0;
        for (DisplayColumn col : displayColumns)
        {
            if (col.getColumnInfo() == null || (!col.getColumnInfo().getName().toLowerCase().startsWith("run") || col.getColumnInfo().getName().indexOf('/') == -1))
            {
                offset++;
            }
        }
        rgn.setOffset(offset);
        List<String> headings = new ArrayList<String>();
        for (MS2Run run : _runs)
        {
            ActionURL url = MS2Controller.getShowRunURL(run.getContainer(), run.getRun());
            headings.add("<a href=\"" + url.getLocalURIString() + "\">" + PageFlowUtil.filter(run.getDescription()) + "</a>");
        }
        rgn.setMultiColumnCaptions(headings);
        rgn.setColSpan((displayColumns.size() - offset) / _runs.size());
        return rgn;
    }

    public List<DisplayColumn> getDisplayColumns()
    {
        List<DisplayColumn> ret = new ArrayList<DisplayColumn>();
        TableInfo table = getTable();
        if (table == null)
            return Collections.emptyList();

        CustomView view = getCustomView();
        List<FieldKey> cols;

        if (_columns != null)
        {
            cols = _columns;
        }
        else if (view != null)
        {
            cols = view.getColumns();
        }
        else
        {
            cols = table.getDefaultVisibleColumns();
        }

        List<FieldKey> nonRunCols = new ArrayList<FieldKey>();
        List<FieldKey> runCols = new ArrayList<FieldKey>();
        int runColCount = 0;
        for (FieldKey col : cols)
        {
            if ("Run".equalsIgnoreCase(col.getParts().get(0)))
            {
                List<String> parts = new ArrayList<String>(col.getParts());
                int runOffset = 0;
                for (MS2Run run : _runs)
                {
                    parts.set(0, "Run" + run.getRun());
                    runCols.add((runColCount + 1) * runOffset + runColCount, FieldKey.fromParts(parts));
                    runOffset++;
                }
                runColCount++;
            }
            else
            {
                nonRunCols.add(col);
            }
        }

        List<FieldKey> newCols = new ArrayList<FieldKey>(nonRunCols);
        newCols.addAll(runCols);
        if (view != null)
        {
            view.setColumns(newCols);
            ret.addAll(getQueryDef().getDisplayColumns(view, table));
        }
        else
        {
            for (ColumnInfo col : QueryService.get().getColumns(table, newCols).values())
            {
                DisplayColumn renderer = col.getRenderer();
                ret.add(renderer);
            }
        }

        return ret;
    }

    public abstract String getComparisonName();
}
