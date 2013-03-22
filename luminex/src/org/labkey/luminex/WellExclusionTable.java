/*
 * Copyright (c) 2011-2013 LabKey Corporation
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
package org.labkey.luminex;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.MultiValuedForeignKey;
import org.labkey.api.data.MultiValuedRenderContext;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpProtocolApplication;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DuplicateKeyException;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayRunDatabaseContext;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.UnauthorizedException;

import java.io.IOException;
import java.io.Writer;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * User: jeckels
 * Date: Jun 29, 2011
 */
public class WellExclusionTable extends AbstractExclusionTable
{
    public WellExclusionTable(LuminexProtocolSchema schema, boolean filter)
    {
        super(LuminexProtocolSchema.getTableInfoWellExclusion(), schema, filter);

        getColumn("DataId").setLabel("Data File");
        getColumn("DataId").setFk(new ExpSchema(schema.getUser(), schema.getContainer()).getDataIdForeignKey());
        
        getColumn("Analytes").setFk(new MultiValuedForeignKey(new LookupForeignKey("WellExclusionId")
        {
            @Override
            public TableInfo getLookupTableInfo()
            {
                return _userSchema.createWellExclusionAnalyteTable();
            }
        }, "AnalyteId"));
        getColumn("Analytes").setUserEditable(false);

        SQLFragment joinSQL = new SQLFragment(" FROM ");
        joinSQL.append(LuminexProtocolSchema.getTableInfoDataRow(), "dr");
        joinSQL.append(" WHERE (dr.Description = ");
        joinSQL.append(ExprColumn.STR_TABLE_ALIAS);
        joinSQL.append(".Description OR (dr.Description IS NULL AND ");
        joinSQL.append(ExprColumn.STR_TABLE_ALIAS);
        joinSQL.append(".Description IS NULL)) AND dr.DataId = ");
        joinSQL.append(ExprColumn.STR_TABLE_ALIAS);
        joinSQL.append(".DataId AND dr.Type = ");
        joinSQL.append(ExprColumn.STR_TABLE_ALIAS);
        joinSQL.append(".Type");

        SQLFragment wellRoleSQL = new SQLFragment("SELECT WellRole FROM (SELECT DISTINCT dr.WellRole");
        wellRoleSQL.append(joinSQL);
        wellRoleSQL.append(") x");
        addColumn(new ExprColumn(this, "Well Role", schema.getDbSchema().getSqlDialect().getSelectConcat(wellRoleSQL, ","), JdbcType.VARCHAR));

        SQLFragment wellSQL = new SQLFragment("SELECT Well FROM (SELECT DISTINCT dr.Well");
        wellSQL.append(joinSQL);
        wellSQL.append(") x");
        ExprColumn wellsCol = new ExprColumn(this, "Wells", schema.getDbSchema().getSqlDialect().getSelectConcat(wellSQL, ","), JdbcType.VARCHAR);
        wellsCol.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            @Override
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                return new DataColumn(colInfo)
                {
                    @Override
                    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
                    {
                        Object o = getDisplayValue(ctx);
                        out.write(null == o ? "&nbsp;" : PageFlowUtil.filter(o.toString()));
                    }

                    @Override
                    public Object getDisplayValue(RenderContext ctx)
                    {
                        Object result = getValue(ctx);
                        if (null != result)
                        {
                            // get the list of unique wells (by splitting the concatenated string)
                            TreeSet<String> uniqueWells = new TreeSet<String>();
                            uniqueWells.addAll(Arrays.asList(result.toString().split(MultiValuedRenderContext.VALUE_DELIMETER_REGEX)));

                            // put the unique wells back into a comma separated string
                            StringBuilder sb = new StringBuilder();
                            String comma = "";
                            for (String well : uniqueWells)
                            {
                                sb.append(comma);
                                sb.append(well);
                                comma = ",";
                            }
                            result = sb.toString();
                        }
                        return result;
                    }
                };
            }
        });
        addColumn(wellsCol);

        List<FieldKey> defaultCols = new ArrayList<FieldKey>(getDefaultVisibleColumns());
        defaultCols.remove(FieldKey.fromParts("ModifiedBy"));
        defaultCols.remove(FieldKey.fromParts("Modified"));
        defaultCols.add(0, FieldKey.fromParts("DataId", "Run"));
        setDefaultVisibleColumns(defaultCols);
    }

    @Override
    protected SQLFragment createContainerFilterSQL(ContainerFilter filter, Container container)
    {
        SQLFragment sql = new SQLFragment("DataId IN (SELECT RowId FROM ");
        sql.append(ExperimentService.get().getTinfoData(), "d");
        sql.append(" WHERE ");
        sql.append(filter.getSQLFragment(getSchema(), "Container", container));
        sql.append(")");
        return sql;
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        return new ExclusionUpdateService(this, getRealTable(), LuminexProtocolSchema.getTableInfoWellExclusionAnalyte(), "WellExclusionId")
        {
            private Set<ExpRun> _runsToRefresh = new HashSet<ExpRun>();

            private Integer getDataId(Map<String, Object> rowMap) throws QueryUpdateServiceException
            {
                Integer dataId = convertToInteger(rowMap.get("DataId"));
                if (dataId == null)
                {
                    throw new QueryUpdateServiceException("No DataId specified");
                }
                return dataId;
            }

            @Override
            protected void checkPermissions(User user, Map<String, Object> rowMap, Class<? extends Permission> permission) throws QueryUpdateServiceException
            {
                ExpData data = getData(rowMap);
                if (!data.getContainer().hasPermission(user, permission))
                {
                    throw new UnauthorizedException();
                }
            }

            private ExpData getData(Map<String, Object> rowMap) throws QueryUpdateServiceException
            {
                Integer dataId = getDataId(rowMap);
                ExpData data = ExperimentService.get().getExpData(dataId);
                if (data == null)
                {
                    throw new QueryUpdateServiceException("No such data file: " + dataId);
                }
                return data;
            }

            @Override
            protected @NotNull ExpRun resolveRun(Map<String, Object> rowMap) throws QueryUpdateServiceException, SQLException
            {
                ExpData data = getData(rowMap);
                ExpProtocolApplication protApp = data.getSourceApplication();
                if (protApp == null)
                {
                    throw new QueryUpdateServiceException("Unable to resolve run for data " + data.getRowId() + ", no source protocol application");
                }
                ExpRun run = protApp.getRun();
                if (run == null)
                {
                    throw new QueryUpdateServiceException("Unable to resolve run for data " + data.getRowId());
                }
                if (!_runsToRefresh.contains(run))
                {
                    String description = rowMap.get("Description") == null ? null : rowMap.get("Description").toString();
                    String type = rowMap.get("Type") == null ? null : rowMap.get("Type").toString();

                    SQLFragment dataRowSQL = new SQLFragment("SELECT COUNT(*) FROM ");
                    dataRowSQL.append(LuminexProtocolSchema.getTableInfoDataRow(), "dr");
                    dataRowSQL.append(" WHERE dr.TitrationId IS NOT NULL AND dr.DataId = ? AND dr.Description ");
                    dataRowSQL.add(data.getRowId());
                    if (description == null)
                    {
                        dataRowSQL.append("IS NULL");
                    }
                    else
                    {
                        dataRowSQL.append("= ?");
                        dataRowSQL.add(description);
                    }

                    dataRowSQL.append(" AND dr.Type ");
                    if (type == null)
                    {
                        dataRowSQL.append("IS NULL");
                    }
                    else
                    {
                        dataRowSQL.append("= ?");
                        dataRowSQL.add(type);
                    }


                    Integer count = Table.executeSingleton(LuminexProtocolSchema.getSchema(), dataRowSQL.getSQL(), dataRowSQL.getParamsArray(), Integer.class);
                    if (count.intValue() > 0)
                    {
                        _runsToRefresh.add(run);
                    }
                }

                return run;
            }

            @Override
            public List<Map<String, Object>> insertRows(User user, Container container, List<Map<String, Object>> rows, BatchValidationException errors, Map<String, Object> extraScriptContext) throws DuplicateKeyException, QueryUpdateServiceException, SQLException
            {
                // Only allow one thread to be running a Luminex transform script and importing its results at a time
                // See issue 17424
                synchronized (LuminexRunCreator.LOCK_OBJECT)
                {
                    List<Map<String, Object>> result = super.insertRows(user, container, rows, errors, extraScriptContext);
                    rerunTransformScripts();
                    return result;
                }
            }

            @Override
            public List<Map<String, Object>> deleteRows(User user, Container container, List<Map<String, Object>> keys, Map<String, Object> extraScriptContext) throws InvalidKeyException, BatchValidationException, QueryUpdateServiceException, SQLException
            {
                // Only allow one thread to be running a Luminex transform script and importing its results at a time
                // See issue 17424
                synchronized (LuminexRunCreator.LOCK_OBJECT)
                {
                    List<Map<String, Object>> result = super.deleteRows(user, container, keys, extraScriptContext);
                    rerunTransformScripts();
                    return result;
                }
            }

            @Override
            public List<Map<String, Object>> updateRows(User user, Container container, List<Map<String, Object>> rows, List<Map<String, Object>> oldKeys, Map<String, Object> extraScriptContext) throws InvalidKeyException, BatchValidationException, QueryUpdateServiceException, SQLException
            {
                // Only allow one thread to be running a Luminex transform script and importing its results at a time
                // See issue 17424
                synchronized (LuminexRunCreator.LOCK_OBJECT)
                {
                    List<Map<String, Object>> result = super.updateRows(user, container, rows, oldKeys, extraScriptContext);
                    rerunTransformScripts();
                    return result;
                }
            }

            private void rerunTransformScripts() throws QueryUpdateServiceException
            {
                try
                {
                    for (ExpRun run : _runsToRefresh)
                    {
                        AssayProvider provider = AssayService.get().getProvider(run);
                        AssayRunDatabaseContext context = provider.createRunDatabaseContext(run, _userSchema.getUser(), null);
                        provider.getRunCreator().saveExperimentRun(context, AssayService.get().findBatch(run), run, false);
                    }
                }
                catch (ExperimentException e)
                {
                    throw new QueryUpdateServiceException(e);
                }
                catch (ValidationException e)
                {
                    throw new QueryUpdateServiceException(e);
                }
            }
        };
    }
}
