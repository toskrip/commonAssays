package org.labkey.flow.controllers.executescript;

import org.labkey.api.action.SpringActionController;
import org.labkey.api.collections.NamedObject;
import org.labkey.api.collections.NamedObjectList;
import org.labkey.api.collections.RowMapFactory;
import org.labkey.api.data.AbstractForeignKey;
import org.labkey.api.data.ActionButton;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.InputColumn;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.MapListResultSet;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.Results;
import org.labkey.api.data.ResultsImpl;
import org.labkey.api.data.SimpleDisplayColumn;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.query.FieldKey;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.SimpleNamedObject;
import org.labkey.api.util.StringExpression;
import org.labkey.api.view.GridView;
import org.labkey.flow.analysis.model.PopulationName;
import org.labkey.flow.analysis.model.Workspace;
import org.labkey.flow.data.FlowFCSFile;
import org.labkey.flow.data.FlowRun;
import org.labkey.flow.util.KeywordUtil;
import org.springframework.validation.Errors;

import java.io.IOException;
import java.io.Writer;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: kevink
 * Date: 10/20/12
 */
public class ResolvedConfirmGridView extends GridView
{
    /*package*/ static String DATAREGION_NAME = "ResolveConfirm";

    static FieldKey MATCHED_FLAG_FIELD_KEY = new FieldKey(null, "MatchedFlag");
    static FieldKey MATCHED_FILE_FIELD_KEY = new FieldKey(null, "MatchedFile");
    static FieldKey SAMPLE_ID_FIELD_KEY = new FieldKey(null, "SampleId");
    static FieldKey SAMPLE_NAME_FIELD_KEY = new FieldKey(null, "SampleName");
    static FieldKey GROUP_NAMES_FIELD_KEY = new FieldKey(null, "GroupNames");

    Map<Integer, FlowRun> _runs = new HashMap<Integer, FlowRun>();

    public ResolvedConfirmGridView(ResolvedSamplesData data, Errors errors)
    {
        this(data.getKeywords(), data.getSamples(), data.getRows(), data.getResolved(), errors);
    }

    public ResolvedConfirmGridView(Collection<String> keywords, List<Workspace.SampleInfo> samples, Map<String, ResolvedSamplesData.ResolvedSample> rows, Map<Workspace.SampleInfo, FlowFCSFile> resolved, Errors errors)
    {
        super(new ResolveConfirmDataRegion(), errors);

        // Create the list of columns
        keywords = KeywordUtil.filterHidden(keywords);
        List<String> columns = new ArrayList<String>();
        columns.add(MATCHED_FLAG_FIELD_KEY.getName());
        columns.add(MATCHED_FILE_FIELD_KEY.getName());
        columns.add(SAMPLE_ID_FIELD_KEY.getName());
        columns.add(SAMPLE_NAME_FIELD_KEY.getName());
        columns.add(GROUP_NAMES_FIELD_KEY.getName());
        columns.addAll(keywords);
        int columnCount = columns.size();
        RowMapFactory factory = new RowMapFactory(columns.toArray(new String[columnCount]));


        // Create the data maps, one for each sample in the workspace
        List<Map<String, Object>> unmatchedList = new ArrayList<Map<String, Object>>(samples.size());
        List<Map<String, Object>> matchedList = new ArrayList<Map<String, Object>>(samples.size());
        for (Workspace.SampleInfo sample : samples)
        {
            Object[] row = new Object[columnCount];
            int i = 0;

            // MatchedFlag and MatchedFile
            ResolvedSamplesData.ResolvedSample matched = rows.get(sample.getSampleId());
            row[i++] = matched != null && matched.hasMatchedFile();
            row[i++] = matched != null && matched.hasMatchedFile() ? matched.getMatchedFile() : null;

            // SampleId and SampleName
            row[i++] = sample.getSampleId();
            row[i++] = sample.getLabel();

            // GroupNames
            String sep = "";
            StringBuilder sb = new StringBuilder();
            for (Workspace.GroupInfo group : sample.getGroups())
            {
                if (group.isAllSamples())
                    continue;
                sb.append(sep).append(group.getGroupName().toString());
                sep = ", ";
            }
            row[i++] = sb.toString();

            // Keywords
            Map<String, String> sampleKeywords = sample.getKeywords();
            for (String keyword : keywords)
                row[i++] = sampleKeywords.get(keyword);

            Map<String, Object> rowMap = factory.getRowMap(row);
            if (matched != null && matched.hasMatchedFile())
                matchedList.add(rowMap);
            else
                unmatchedList.add(rowMap);
        }

        // Combine unmatched and matched lists (unmatched are first so the user sees them)
        List<Map<String, Object>> maps = new ArrayList<Map<String, Object>>(samples.size());
        maps.addAll(unmatchedList);
        maps.addAll(matchedList);

        // Initialize the ResultSet and DataRegion
        ResultSet rs = new MapListResultSet(maps);
        Results results = new ResultsImpl(rs);
        setResults(results);

        ResolveConfirmDataRegion dr = (ResolveConfirmDataRegion)getDataRegion();
        dr.setName(DATAREGION_NAME);
        dr.setShowFilters(false);
        dr.setSortable(false);
        dr.setShowPagination(true);

        dr.sampleCount = samples.size();
        dr.matchedCount = matchedList.size();
        dr.unmatchedCount = unmatchedList.size();

        // Populate selection state with the sample ids for the selected rows.
        // If the sample id selector value is present in the selection state, the row is checked.
        //dr.setRecordSelectorValueColumns(SAMPLE_ID_FIELD_KEY.getName());
        dr.setShowRecordSelectors(true);
        Set<String> selected = new HashSet<String>();
        for (Map.Entry<String, ResolvedSamplesData.ResolvedSample> row : rows.entrySet())
        {
            if (row.getValue().isSelected())
                selected.add(row.getKey());
        }
        getRenderContext().setAllSelected(selected);

        ButtonBar buttonBar = new ButtonBar();
        ActionButton button = new ActionButton("Update Matches");
        button.setActionType(ActionButton.Action.POST);
        // Set the form's step to the previous step and submit.
        button.setScript(
                "document.forms[\"" + ImportAnalysisForm.NAME + "\"].elements[\"step\"].value = " + AnalysisScriptController.ImportAnalysisStep.SELECT_FCSFILES.getNumber() + "; " +
                "document.forms[\"" + ImportAnalysisForm.NAME + "\"].submit();", false);
        buttonBar.add(button);
        dr.setButtonBar(buttonBar);
        dr.setButtonBarPosition(DataRegion.ButtonBarPosition.BOTH);

        // Add MatchedFlag column
        DisplayColumn dc = new MatchedFlagDisplayColumn();
        dr.addDisplayColumn(dc);

        // Add Matched column
        ColumnInfo matchCol = new ColumnInfo(MATCHED_FILE_FIELD_KEY, null, JdbcType.INTEGER);
        matchCol.setLabel("Matched FCS File");
        matchCol.setFk(new FCSFilesFilesForeignKey(FlowFCSFile.getOriginal(getViewContext().getContainer())));
        matchCol.setInputType("select");
        dc = new MatchedFileDisplayColumn(matchCol);
        dr.addDisplayColumn(dc);

        // Add SampleName column
        dc = new SimpleDisplayColumn("${" + SAMPLE_NAME_FIELD_KEY.getName() + "}");
        dc.setCaption("Name");
        dr.addDisplayColumn(dc);

        // Add GroupNames column
        dc = new SimpleDisplayColumn("${" + GROUP_NAMES_FIELD_KEY.getName() + "}");
        dc.setCaption("Groups");
        dr.addDisplayColumn(dc);

        // Add keyword columns
        for (String keyword : keywords)
        {
            dc = new SimpleDisplayColumn("${" + keyword + "}");
            dc.setCaption(keyword);
            dr.addDisplayColumn(dc);
        }
    }

    private static class ResolveConfirmDataRegion extends DataRegion
    {
        protected int sampleCount = 0;
        protected int unmatchedCount = 0;
        protected int matchedCount = 0;

        // XXX: This is the lamest way to add new messages to the DataRegion.
        @Override
        protected void renderHeaderScript(RenderContext ctx, Writer writer, Map<String, String> messages, boolean showRecordSelectors) throws IOException
        {
            String matchedMsg;
            if (matchedCount == sampleCount)
                matchedMsg = String.format("Matched all %d samples.", sampleCount);
            else
                matchedMsg = String.format("Matched %d of %d samples.", matchedCount, sampleCount);
            messages.put("matches", matchedMsg);
            super.renderHeaderScript(ctx, writer, messages, showRecordSelectors);
        }

        @Override
        protected void renderFormHeader(RenderContext ctx, Writer out, int mode) throws IOException
        {
            renderHiddenFormFields(ctx, out, mode);
        }

        @Override
        protected void renderFormEnd(RenderContext ctx, Writer out) throws IOException
        {
            // No-op.  Don't close the form.
        }

        @Override
        protected String getRecordSelectorName(RenderContext ctx)
        {
            // Bind select checkbox to ImportAnalyisForm.resolvedSamples.select
            String sampleId = ctx.get(SAMPLE_ID_FIELD_KEY, String.class);
            return "resolvedSamples.rows[" + sampleId + "].selected";
        }

        @Override
        protected String getRecordSelectorValue(RenderContext ctx)
        {
            return "1";
        }

        @Override
        protected boolean isRecordSelectorChecked(RenderContext ctx, String checkboxValue)
        {
            String sampleId = ctx.get(SAMPLE_ID_FIELD_KEY, String.class);
            Set<String> selectedValues = ctx.getAllSelected();
            return selectedValues.contains(sampleId);
        }

        @Override
        protected void renderExtraRecordSelectorContent(RenderContext ctx, Writer out) throws IOException
        {
            // Add a hidden input for spring form binding -- if this value is posed, the row was unchecked.
            out.write("<input type=hidden name='");
            out.write(SpringActionController.FIELD_MARKER + getRecordSelectorName(ctx));
            out.write("' value=\"0\"");
        }

        @Override
        protected boolean isErrorRow(RenderContext ctx, int rowIndex)
        {
            // Render unmatched rows as errors
            Boolean match = ctx.get(MATCHED_FLAG_FIELD_KEY, Boolean.class);
            if (match != null && match)
                return false;

            // If the row isn't selected and won't be imported, don't render as an error.
            String checkboxName = getRecordSelectorValue(ctx);
            boolean checked = isRecordSelectorChecked(ctx, checkboxName);
            if (!checked)
                return false;

            // Unmatched and row is selected for import.
            return true;
        }

    }

    private class MatchedFlagDisplayColumn extends SimpleDisplayColumn
    {

        public MatchedFlagDisplayColumn()
        {
            super();
        }

        @Override
        public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
        {
            Boolean match = ctx.get(MATCHED_FLAG_FIELD_KEY, Boolean.class);
            if (match != null)
            {
                out.write("<img src=\"");
                out.write(AppProps.getInstance().getContextPath());
                if (match)
                {
                    out.write("/_images/check.png\" />");
                    String fileName = ctx.get(SAMPLE_NAME_FIELD_KEY, String.class);
                    out.write(PageFlowUtil.helpPopup("Matched", "Matched the previously imported FCS file '" + fileName + "'", true));
                }
                else
                {
                    out.write("/_images/cancel.png\" />");
                    out.write(PageFlowUtil.helpPopup("Not matched", "Failed to match a previously imported FCS file.  Please manually select a matching FCS file or skip importing this row.", true));
                }
            }
        }
    }

    // Simple lookup to list of original FlowFCSFiles
    private class FCSFilesFilesForeignKey extends AbstractForeignKey
    {
        List<FlowFCSFile> _files;
        NamedObjectList _list;

        FCSFilesFilesForeignKey(List<FlowFCSFile> files)
        {
            _files = files;

            _list = new NamedObjectList();
            for (FlowFCSFile file : _files)
                _list.put(new SimpleNamedObject(String.valueOf(file.getRowId()), file));
        }

        @Override
        public ColumnInfo createLookupColumn(ColumnInfo parent, String displayField)
        {
            return null;
        }

        @Override
        public TableInfo getLookupTableInfo()
        {
            return null;
        }

        @Override
        public StringExpression getURL(ColumnInfo parent)
        {
            return null;
        }

        @Override
        public NamedObjectList getSelectList()
        {
            return _list;
        }
    }

    private class MatchedFileDisplayColumn extends InputColumn
    {
        public MatchedFileDisplayColumn(ColumnInfo col)
        {
            super(col);
        }

        @Override
        public String getFormFieldName(RenderContext ctx)
        {
            // Bind select combobox to ImportAnalyisForm.resolvedSamples.matchedFile
            String sampleId = ctx.get(SAMPLE_ID_FIELD_KEY, String.class);
            return "resolvedSamples.rows[" + sampleId + "].matchedFile";
        }

        @Override
        protected String getSelectInputDisplayValue(NamedObject entry)
        {
            Object o = entry.getObject();
            if (!(o instanceof FlowFCSFile))
                return null;

            FlowFCSFile file = (FlowFCSFile)o;
            ExpData data = file.getData();
            FlowRun run = null;
            if (data != null && data.getRunId() != null)
            {
                run = _runs.get(data.getRunId());
                if (run == null)
                    _runs.put(data.getRunId(), run = file.getRun());
            }

            if (run != null)
                return file.getName() + " (" + run.getName() + ")";
            else
                return file.getName();
        }

    }
}

