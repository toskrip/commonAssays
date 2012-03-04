/*
 * Copyright (c) 2008-2012 LabKey Corporation
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

package org.labkey.microarray;

import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.IconDisplayColumn;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SchemaTableInfo;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ExperimentUrls;
import org.labkey.api.exp.query.ExpRunTable;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.SimpleUserSchema;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.study.actions.AssayDetailRedirectAction;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.microarray.assay.MicroarrayAssayProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class MicroarraySchema extends SimpleUserSchema
{
    public static final String SCHEMA_NAME = "Microarray";
    public static final String SCHMEA_DESCR = "Contains data about Microarray assay runs";
    public static final String TABLE_RUNS = "MicroarrayRuns";
    public static final String TABLE_GEO_PROPS = "Geo_Properties";

    private ExpSchema _expSchema;
    public static final String QC_REPORT_COLUMN_NAME = "QCReport";
    public static final String THUMBNAIL_IMAGE_COLUMN_NAME = "ThumbnailImage";

    public MicroarraySchema(User user, Container container)
    {
        super(SCHEMA_NAME, SCHMEA_DESCR, user, container, getSchema());
        _expSchema = new ExpSchema(user, container);
    }


    static public void register()
    {
        DefaultSchema.registerProvider(SCHEMA_NAME, new DefaultSchema.SchemaProvider() {
            public QuerySchema getSchema(DefaultSchema schema)
            {
                return new MicroarraySchema(schema.getUser(), schema.getContainer());
            }
        });
    }

    public Set<String> getTableNames()
    {
        CaseInsensitiveHashSet hs = new CaseInsensitiveHashSet();
        hs.add(TABLE_RUNS);
        hs.add(TABLE_GEO_PROPS);
        return hs;
    }

    public TableInfo createTable(String name)
    {
        if (TABLE_RUNS.equalsIgnoreCase(name))
        {
            return createRunsTable();
        }

        if(getTableNames().contains(name))
        {
            SchemaTableInfo tableInfo = getSchema().getTable(name);
            return new SimpleUserSchema.SimpleTable(this, tableInfo);
        }

        return null;
    }

    public ExpRunTable createRunsTable()
    {
        ExpRunTable result = _expSchema.getRunsTable();
        result.getColumn(ExpRunTable.Column.Name).setURL(new DetailsURL(new ActionURL(AssayDetailRedirectAction.class, _expSchema.getContainer()), Collections.singletonMap("runId", "rowId")));

        result.setProtocolPatterns("urn:lsid:%:" + MicroarrayAssayProvider.PROTOCOL_PREFIX + ".%");

        SQLFragment thumbnailSQL = new SQLFragment("(SELECT MIN(d.RowId)\n" +
                "\nFROM " + ExperimentService.get().getTinfoData() + " d " +
                "\nWHERE d.RunId = " + ExprColumn.STR_TABLE_ALIAS + ".RowId AND d.LSID LIKE '%:" + MicroarrayModule.THUMBNAIL_INPUT_TYPE.getNamespacePrefix() + "%')");
        ColumnInfo thumbnailColumn = new ExprColumn(result, THUMBNAIL_IMAGE_COLUMN_NAME, thumbnailSQL, JdbcType.INTEGER);
        thumbnailColumn.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                ActionURL url = PageFlowUtil.urlProvider(ExperimentUrls.class).getShowFileURL(getContainer());
                return new IconDisplayColumn(colInfo, 18, 18, url, "rowId", AppProps.getInstance().getContextPath() + "/microarray/images/microarrayThumbnailIcon.png");
            }
        });
        result.addColumn(thumbnailColumn);

        SQLFragment qcReportSQL = new SQLFragment("(SELECT MIN(d.RowId)\n" +
                "\nFROM " + ExperimentService.get().getTinfoData() + " d " +
                "\nWHERE d.RunId = " + ExprColumn.STR_TABLE_ALIAS + ".RowId AND d.LSID LIKE '%:" + MicroarrayModule.QC_REPORT_INPUT_TYPE.getNamespacePrefix() + "%')");
        ColumnInfo qcReportColumn = new ExprColumn(result, QC_REPORT_COLUMN_NAME, qcReportSQL, JdbcType.INTEGER);
        qcReportColumn.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                ActionURL url = PageFlowUtil.urlProvider(ExperimentUrls.class).getShowFileURL(getContainer());
                url.addParameter("inline", "true");
                return new IconDisplayColumn(colInfo, 18, 18, url, "rowId", AppProps.getInstance().getContextPath() + "/microarray/images/qcReportIcon.png");
            }
        });
        result.addColumn(qcReportColumn);

        List<FieldKey> defaultCols = new ArrayList<FieldKey>(result.getDefaultVisibleColumns());
        defaultCols.remove(FieldKey.fromParts(qcReportColumn.getName()));
        defaultCols.remove(FieldKey.fromParts(thumbnailColumn.getName()));
        defaultCols.add(2, FieldKey.fromParts(qcReportColumn.getName()));
        defaultCols.add(2, FieldKey.fromParts(thumbnailColumn.getName()));
        result.setDefaultVisibleColumns(defaultCols);

        result.setDescription("Contains a row per microarray analysis result loaded in this folder.");

        return result;
    }

    public static DbSchema getSchema()
    {
        return DbSchema.get("microarray");
    }
}
