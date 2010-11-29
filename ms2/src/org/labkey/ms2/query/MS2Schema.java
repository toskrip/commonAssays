/*
 * Copyright (c) 2006-2010 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.*;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exp.query.ExpRunTable;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.query.*;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.util.StringExpression;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.ms2.MS2Controller;
import org.labkey.ms2.MS2Manager;
import org.labkey.ms2.MS2Run;
import org.labkey.ms2.ProteinGroupProteins;
import org.labkey.ms2.metadata.MassSpecMetadataAssayProvider;
import org.labkey.ms2.protein.ProteinManager;

import javax.servlet.http.HttpServletRequest;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;
import java.util.*;

/**
 * User: jeckels
 * Date: Sep 25, 2006
 */
public class MS2Schema extends UserSchema
{
    public static final String SCHEMA_NAME = "ms2";
    public static final String SCHEMA_DESCR = "Contains data about MS2 runs, including detected peptides and proteins";

    private static final String PROTOCOL_PATTERN_PREFIX = "urn:lsid:%:Protocol.%:";

    public static final String MASCOT_PROTOCOL_OBJECT_PREFIX = "MS2.Mascot";
    public static final String SEQUEST_PROTOCOL_OBJECT_PREFIX = "MS2.Sequest";
    public static final String XTANDEM_PROTOCOL_OBJECT_PREFIX = "MS2.XTandem";
    public static final String IMPORTED_SEARCH_PROTOCOL_OBJECT_PREFIX = "MS2.ImportedSearch";
    public static final String SAMPLE_PREP_PROTOCOL_OBJECT_PREFIX = "MS2.PreSearch.";

    private static final Set<String> HIDDEN_PEPTIDE_MEMBERSHIPS_COLUMN_NAMES = new CaseInsensitiveHashSet("PeptideId");

    private ProteinGroupProteins _proteinGroupProteins = new ProteinGroupProteins();
    private List<MS2Run> _runs;

    private static final Set<String> TABLE_NAMES;

    static
    {
        Set<String> names = new HashSet<String>();
        for (TableType tableType : TableType.values())
        {
            names.add(tableType.toString());
        }
        TABLE_NAMES = Collections.unmodifiableSet(names);
    }

    public static void register()
    {
        DefaultSchema.registerProvider(SCHEMA_NAME, new DefaultSchema.SchemaProvider()
        {
            public QuerySchema getSchema(DefaultSchema schema)
            {
                return new MS2Schema(schema.getUser(), schema.getContainer());
            }
        });
    }

    public MS2Schema(User user, Container container)
    {
        super(SCHEMA_NAME, SCHEMA_DESCR, user, container, ExperimentService.get().getSchema());
    }

    public enum TableType
    {
        SamplePrepRuns
        {
            public ExpRunTable createTable(MS2Schema ms2Schema)
            {
                ExpRunTable result = ExperimentService.get().createRunTable(SamplePrepRuns.toString(), ms2Schema);
                result.populate();
                // Include the old XAR-based and the new assay-based
                result.setDescription("Contains one row per experimental metadata attached to source spectra files.");
                result.setProtocolPatterns(PROTOCOL_PATTERN_PREFIX + SAMPLE_PREP_PROTOCOL_OBJECT_PREFIX + "%", "urn:lsid:%:" + MassSpecMetadataAssayProvider.PROTOCOL_LSID_NAMESPACE_PREFIX + ".Folder-%:%");
                return result;
            }
        },
        ImportedSearchRuns
        {
            public ExpRunTable createTable(MS2Schema ms2Schema)
            {
                ExpRunTable searchTable = ms2Schema.createSearchTable(ImportedSearchRuns.toString(), ContainerFilter.CURRENT, IMPORTED_SEARCH_PROTOCOL_OBJECT_PREFIX);
                searchTable.setDescription("Contains one row per externally-generated MS2 search result imported in this folder.");
                return searchTable;
            }
        },
        XTandemSearchRuns
        {
            public ExpRunTable createTable(MS2Schema ms2Schema)
            {
                ExpRunTable searchTable = ms2Schema.createSearchTable(XTandemSearchRuns.toString(), ContainerFilter.CURRENT, XTANDEM_PROTOCOL_OBJECT_PREFIX);
                searchTable.setDescription("Contains one row per X!Tandem search result loaded in this folder.");
                return searchTable;
            }
        },
        MascotSearchRuns
        {
            public ExpRunTable createTable(MS2Schema ms2Schema)
            {
                ExpRunTable searchTable = ms2Schema.createSearchTable(MascotSearchRuns.toString(), ContainerFilter.CURRENT, MASCOT_PROTOCOL_OBJECT_PREFIX);
                searchTable.setDescription("Contains one row per Mascot search results loaded in this folder.");
                return searchTable;
            }
        },
        SequestSearchRuns
        {
            public ExpRunTable createTable(MS2Schema ms2Schema)
            {
                ExpRunTable searchTable = ms2Schema.createSearchTable(SequestSearchRuns.toString(), ContainerFilter.CURRENT, SEQUEST_PROTOCOL_OBJECT_PREFIX);
                searchTable.setDescription("Contains one row per Sequest search result loaded in this folder.");
                return searchTable;
            }
        },
        MS2SearchRuns
        {
            public ExpRunTable createTable(MS2Schema ms2Schema)
            {
                ExpRunTable runsTable = ms2Schema.createRunsTable(MS2SearchRuns.toString(), ContainerFilter.CURRENT);
                runsTable.setDescription("Contains one row per MS2 search result, regardless of source, loaded in this folder.");
                return runsTable;
            }
        },
        Peptides
        {
            public TableInfo createTable(MS2Schema ms2Schema)
            {
                return ms2Schema.createPeptidesTable(ContainerFilter.CURRENT);
            }
        },
        ProteinGroups
        {
            public TableInfo createTable(MS2Schema ms2Schema)
            {
                ProteinGroupTableInfo result = new ProteinGroupTableInfo(ms2Schema);
                result.addContainerCondition(ms2Schema.getContainer(), ms2Schema.getUser(), false);
                return result;
            }
        },
        Sequences
        {
            public TableInfo createTable(MS2Schema ms2Schema)
            {
                return ms2Schema.createSequencesTable();
            }
        };

        public abstract TableInfo createTable(MS2Schema ms2Schema);
    }

    public enum HiddenTableType
    {
        PeptidesFilter
        {
            public TableInfo createTable(MS2Schema ms2Schema)
            {
                return ms2Schema.createPeptidesTable(ContainerFilter.CURRENT);
            }
        },
        ProteinGroupsFilter
        {
            public TableInfo createTable(MS2Schema ms2Schema)
            {
                return ms2Schema.createProteinGroupsForRunTable(null);
            }
        },
        ProteinGroupsForSearch
        {
            public TableInfo createTable(MS2Schema ms2Schema)
            {
                return ms2Schema.createProteinGroupsForSearchTable();
            }
        },
        ProteinGroupsForRun
        {
            public TableInfo createTable(MS2Schema ms2Schema)
            {
                return ms2Schema.createProteinGroupsForRunTable(false);
            }
        },
        CompareProteinProphet
        {
            public TableInfo createTable(MS2Schema ms2Schema)
            {
                return ms2Schema.createProteinProphetCompareTable(null, null);
            }
        },
        ComparePeptides
        {
            public TableInfo createTable(MS2Schema ms2Schema)
            {
                return ms2Schema.createPeptidesCompareTable(false, null, null);
            }
        },
        ProteinProphetCrosstab
        {
            public TableInfo createTable(MS2Schema ms2Schema)
            {
                return ms2Schema.createProteinProphetCrosstabTable(null, null);
            }
        },
        ProteinProphetNormalizedCrosstab
        {
            public TableInfo createTable(MS2Schema ms2Schema)
            {
                return ms2Schema.createNormalizedProteinProphetComparisonTable(null, null);
            }
        };

        public abstract TableInfo createTable(MS2Schema ms2Schema);

    }

    public Set<String> getTableNames()
    {
        return TABLE_NAMES;
    }

    public ProteinGroupProteins getProteinGroupProteins()
    {
        return _proteinGroupProteins;
    }

    public TableInfo createTable(String name)
    {
        for (TableType tableType : TableType.values())
        {
            if (tableType.toString().equalsIgnoreCase(name))
            {
                return tableType.createTable(this);
            }
        }
        for (HiddenTableType tableType : HiddenTableType.values())
        {
            if (tableType.toString().equalsIgnoreCase(name))
            {
                return tableType.createTable(this);
            }
        }

        SpectraCountConfiguration config = SpectraCountConfiguration.findByTableName(name);
        if (config != null)
        {
            return createSpectraCountTable(config, null, null);
        }

        return null;
    }

    public ComparePeptideTableInfo createPeptidesCompareTable(boolean forExport, HttpServletRequest request, String peptideViewName)
    {
        return new ComparePeptideTableInfo(this, _runs, forExport, request, peptideViewName);
    }

    public CompareProteinProphetTableInfo createProteinProphetCompareTable(HttpServletRequest request, String peptideViewName)
    {
        return new CompareProteinProphetTableInfo(this, _runs, false, request, peptideViewName);
    }

    public ExpRunTable createRunsTable(String name, ContainerFilter filter)
    {
        return createSearchTable(name, filter, XTANDEM_PROTOCOL_OBJECT_PREFIX, MASCOT_PROTOCOL_OBJECT_PREFIX, SEQUEST_PROTOCOL_OBJECT_PREFIX , IMPORTED_SEARCH_PROTOCOL_OBJECT_PREFIX);
    }

    public SpectraCountTableInfo createSpectraCountTable(SpectraCountConfiguration config, ViewContext context, MS2Controller.SpectraCountForm form)
    {
        return new SpectraCountTableInfo(this, config, context, form);
    }

    public ProteinGroupTableInfo createProteinGroupsForSearchTable()
    {
        ProteinGroupTableInfo result = new ProteinGroupTableInfo(this);
        List<FieldKey> defaultColumns = new ArrayList<FieldKey>(result.getDefaultVisibleColumns());
        defaultColumns.add(0, FieldKey.fromParts("ProteinProphet","Run"));
        defaultColumns.add(0, FieldKey.fromParts("ProteinProphet", "Run", "Folder"));
        result.setDefaultVisibleColumns(defaultColumns);
        return result;
    }

    public ProteinGroupTableInfo createProteinGroupsForRunTable(String alias)
    {
        return createProteinGroupsForRunTable(true);
    }

    public ProteinGroupTableInfo createProteinGroupsForRunTable(boolean includeFirstProteinColumn)
    {
        ProteinGroupTableInfo result = new ProteinGroupTableInfo(this, includeFirstProteinColumn);
        result.addProteinsColumn();
        List<FieldKey> defaultColumns = new ArrayList<FieldKey>(result.getDefaultVisibleColumns());
        defaultColumns.add(FieldKey.fromParts("Proteins", "Protein"));
        defaultColumns.add(FieldKey.fromParts("Proteins", "Protein", "BestGeneName"));
        defaultColumns.add(FieldKey.fromParts("Proteins", "Protein", "Mass"));
        defaultColumns.add(FieldKey.fromParts("Proteins", "Protein", "Description"));
        result.setDefaultVisibleColumns(defaultColumns);
        return result;
    }

    protected FilteredTable createProteinGroupMembershipTable(final MS2Controller.PeptideFilteringComparisonForm form, final ViewContext context, boolean filterByRuns)
    {
        FilteredTable result = new FilteredTable(MS2Manager.getTableInfoProteinGroupMemberships());
        result.wrapAllColumns(true);

        result.getColumn("ProteinGroupId").setFk(new LookupForeignKey("RowId")
        {
            public TableInfo getLookupTableInfo()
            {
                ProteinGroupTableInfo result = createProteinGroupsForRunTable(null);

                result.removeColumn(result.getColumn("Proteins"));
                result.removeColumn(result.getColumn("FirstProtein"));

                SQLFragment totalSQL;
                SQLFragment uniqueSQL;

                if (form != null && form.isPeptideProphetFilter() && form.getPeptideProphetProbability() != null)
                {
                    totalSQL = new SQLFragment("(CASE WHEN " + ExprColumn.STR_TABLE_ALIAS + ".RowId IS NULL THEN NULL ELSE (SELECT COUNT(pd.RowId) FROM " + MS2Manager.getTableInfoPeptidesData() + " pd, ");
                    totalSQL.append(MS2Manager.getTableInfoPeptideMemberships() + " pm WHERE pd.RowId = pm.PeptideId AND pd.PeptideProphet >= " + form.getPeptideProphetProbability() + " AND pm.ProteinGroupId = " + ExprColumn.STR_TABLE_ALIAS + ".RowId) END)");

                    uniqueSQL = new SQLFragment("(CASE WHEN " + ExprColumn.STR_TABLE_ALIAS + ".RowId IS NULL THEN NULL ELSE (SELECT COUNT(DISTINCT TrimmedPeptide) FROM " + MS2Manager.getTableInfoPeptidesData() + " pd, ");
                    uniqueSQL.append(MS2Manager.getTableInfoPeptideMemberships() + " pm WHERE pd.RowId = pm.PeptideId AND pd.PeptideProphet >= " + form.getPeptideProphetProbability() + " AND pm.ProteinGroupId = " + ExprColumn.STR_TABLE_ALIAS + ".RowId) END)");
                }
                else if (form != null && form.isCustomViewPeptideFilter())
                {
                    totalSQL = new SQLFragment("(CASE WHEN " + ExprColumn.STR_TABLE_ALIAS + ".RowId IS NULL THEN NULL ELSE (SELECT COUNT(p.RowId) FROM " + MS2Manager.getTableInfoPeptideMemberships() + " pm, ");
                    totalSQL.append("(");
                    totalSQL.append(getPeptideSelectSQL(context.getRequest(), form.getPeptideCustomViewName(context), Arrays.asList(FieldKey.fromParts("RowId"), FieldKey.fromParts("TrimmedPeptide"))));
                    totalSQL.append(") p ");
                    totalSQL.append("WHERE p.RowId = pm.PeptideId AND pm.ProteinGroupId = " + ExprColumn.STR_TABLE_ALIAS + ".RowId) END)");

                    uniqueSQL = new SQLFragment("(CASE WHEN " + ExprColumn.STR_TABLE_ALIAS + ".RowId IS NULL THEN NULL ELSE (SELECT COUNT(DISTINCT p.TrimmedPeptide) FROM " + MS2Manager.getTableInfoPeptideMemberships() + " pm, ");
                    uniqueSQL.append("(");
                    uniqueSQL.append(getPeptideSelectSQL(context.getRequest(), form.getPeptideCustomViewName(context), Arrays.asList(FieldKey.fromParts("RowId"), FieldKey.fromParts("TrimmedPeptide"))));
                    uniqueSQL.append(") p ");
                    uniqueSQL.append("WHERE p.RowId = pm.PeptideId AND pm.ProteinGroupId = " + ExprColumn.STR_TABLE_ALIAS + ".RowId) END)");
                }
                else
                {
                    totalSQL = new SQLFragment("(CASE WHEN " + ExprColumn.STR_TABLE_ALIAS + ".RowId IS NULL THEN NULL ELSE (SELECT COUNT(pd.RowId) FROM " + MS2Manager.getTableInfoPeptidesData() + " pd, ");
                    totalSQL.append(MS2Manager.getTableInfoPeptideMemberships() + " pm WHERE pd.RowId = pm.PeptideId AND pm.ProteinGroupId = " + ExprColumn.STR_TABLE_ALIAS + ".RowId) END)");

                    uniqueSQL = new SQLFragment("(CASE WHEN " + ExprColumn.STR_TABLE_ALIAS + ".RowId IS NULL THEN NULL ELSE (SELECT COUNT(DISTINCT TrimmedPeptide) FROM " + MS2Manager.getTableInfoPeptidesData() + " pd, ");
                    uniqueSQL.append(MS2Manager.getTableInfoPeptideMemberships() + " pm WHERE pd.RowId = pm.PeptideId AND pm.ProteinGroupId = " + ExprColumn.STR_TABLE_ALIAS + ".RowId) END)");
                }

                result.addColumn(new ExprColumn(result, "TotalFilteredPeptides", totalSQL, Types.BIGINT));
                result.addColumn(new ExprColumn(result, "UniqueFilteredPeptides", uniqueSQL, Types.BIGINT));

                return result;
            }
        });

        result.getColumn("SeqId").setLabel("Protein");
        result.getColumn("SeqId").setFk(new LookupForeignKey("SeqId")
        {
            public TableInfo getLookupTableInfo()
            {
                SequencesTableInfo result = createSequencesTable();
                // This is a horrible hack to try to deal with https://www.labkey.org/issues/home/Developer/issues/details.view?issueId=5237
                // Performance on a SQLServer installation with a large number of runs and sequences is much better with
                // this condition because it causes the query plan to flip to something that does a much more efficient
                // join with the sequences tables. However, adding it significantly degrades performance on my admittedly
                // small (though not tiny) Postgres dev database
                if (_runs != null && MS2Manager.getSchema().getSqlDialect().isSqlServer())
                {
                    SQLFragment sql = new SQLFragment();
                    sql.append("(SeqId IN (SELECT SeqId FROM " + ProteinManager.getTableInfoFastaSequences() + " WHERE FastaId IN (SELECT FastaId FROM ");
                    sql.append(MS2Manager.getTableInfoRuns() + " WHERE Run IN ");
                    appendRunInClause(sql);
                    sql.append(")))");

                    result.addCondition(sql, "SeqId");
                }
                return result;
            }
        });

        if (_runs != null && filterByRuns)
        {
            SQLFragment sql = new SQLFragment("ProteinGroupId IN (SELECT pg.RowId FROM ");
            sql.append(MS2Manager.getTableInfoProteinGroups() + " pg, " + MS2Manager.getTableInfoProteinProphetFiles() + " ppf ");
            sql.append(" WHERE pg.ProteinProphetFileId = ppf.RowId AND ppf.Run IN ");
            appendRunInClause(sql);
            sql.append(")");
            result.addCondition(sql, "ProteinGroupId");
        }

        if (form != null)
        {
            if (form.isPeptideProphetFilter() && form.getPeptideProphetProbability() != null)
            {
                SQLFragment sql = new SQLFragment("ProteinGroupID IN (SELECT pm.ProteinGroupID FROM ");
                sql.append(MS2Manager.getTableInfoPeptideMemberships() + " pm ");
                sql.append(", " + MS2Manager.getTableInfoPeptidesData() + " pd, ");
                sql.append(MS2Manager.getTableInfoFractions() + " f ");
                sql.append("WHERE f.Fraction = pd.Fraction AND f.Run IN ");
                appendRunInClause(sql);
                sql.append(" AND pd.RowId = pm.PeptideId AND pd.peptideprophet >= ");
                sql.append(form.getPeptideProphetProbability());
                sql.append(")");
                result.addCondition(sql, "ProteinGroupId");
            }
            else if (form.isCustomViewPeptideFilter())
            {
                SQLFragment sql = new SQLFragment("ProteinGroupID IN (SELECT pm.ProteinGroupID FROM ");
                sql.append(MS2Manager.getTableInfoPeptideMemberships() + " pm ");
                sql.append(" WHERE pm.PeptideId IN (");
                sql.append(getPeptideSelectSQL(context.getRequest(), form.getPeptideCustomViewName(context), Collections.singletonList(FieldKey.fromParts("RowId"))));
                sql.append("))");
                result.addCondition(sql, "ProteinGroupId");
            }

            if (form.isProteinProphetFilter() && form.getProteinProphetProbability() != null)
            {
                SQLFragment sql = new SQLFragment("ProteinGroupID IN (SELECT pg.RowId FROM ");
                sql.append(MS2Manager.getTableInfoProteinGroups() + " pg ");
                sql.append("WHERE pg.GroupProbability >= ");
                sql.append(form.getProteinProphetProbability());
                sql.append(")");
                result.addCondition(sql, "ProteinGroupId");
            }
            else if (form.isCustomViewProteinGroupFilter())
            {
                SQLFragment sql = new SQLFragment("ProteinGroupID IN (");
                getProteinGroupSelectSQL(form, context, sql);
                sql.append(")");
                result.addCondition(sql, "ProteinGroupId");
            }
        }

        return result;
    }

    public void getProteinGroupSelectSQL(MS2Controller.PeptideFilteringComparisonForm form, ViewContext context, SQLFragment sql)
    {
        QueryDefinition queryDef = QueryService.get().createQueryDefForTable(this, HiddenTableType.ProteinGroupsFilter.toString());
        SimpleFilter filter = new SimpleFilter();
        CustomView view = queryDef.getCustomView(getUser(), context.getRequest(), form.getProteinGroupCustomViewName(context));
        if (view != null)
        {
            ActionURL url = new ActionURL();
            view.applyFilterAndSortToURL(url, "InternalName");
            filter.addUrlFilters(url, "InternalName");
        }
        ProteinGroupTableInfo tableInfo = new ProteinGroupTableInfo(this, false);
        tableInfo.setContainerFilter(ContainerFilter.EVERYTHING);
        sql.append(getSelectSQL(tableInfo, filter, Collections.singleton(FieldKey.fromParts("RowId"))));
    }

    public void appendRunInClause(SQLFragment sql)
    {
        sql.append("(");
        if (_runs.isEmpty())
        {
            sql.append("-1");
        }
        else
        {
            String separator = "";
            for (MS2Run run : _runs)
            {
                sql.append(separator);
                separator = ", ";
                sql.append(run.getRun());
            }
        }
        sql.append(")");
    }

    protected TableInfo createPeptideMembershipsTable()
    {
        TableInfo info = MS2Manager.getTableInfoPeptideMemberships();
        FilteredTable result = new FilteredTable(info);
        for (ColumnInfo col : info.getColumns())
        {
            ColumnInfo newColumn = result.addWrapColumn(col);
            if (HIDDEN_PEPTIDE_MEMBERSHIPS_COLUMN_NAMES.contains(newColumn.getName()))
            {
                newColumn.setHidden(true);
            }
        }
        LookupForeignKey fk = new LookupForeignKey("RowId")
        {
            public TableInfo getLookupTableInfo()
            {
                ProteinGroupTableInfo result = new ProteinGroupTableInfo(MS2Schema.this);
                result.getColumn("ProteinProphet").setHidden(true);
                result.addProteinDetailColumns();

                return result;
            }
        };
        fk.setPrefixColumnCaption(false);
        result.getColumn("ProteinGroupId").setFk(fk);
        return result;
    }

    protected TableInfo createFractionsTable()
    {
        SqlDialect dialect = MS2Manager.getSqlDialect();
        FilteredTable result = new FilteredTable(MS2Manager.getTableInfoFractions());
        result.wrapAllColumns(true);

        SQLFragment fractionNameSQL = new SQLFragment(dialect.getSubstringFunction(ExprColumn.STR_TABLE_ALIAS + ".FileName", "1", dialect.getStringIndexOfFunction("'.'", ExprColumn.STR_TABLE_ALIAS + ".FileName") + "- 1"));

        ColumnInfo fractionName = new ExprColumn(result, "FractionName", fractionNameSQL, Types.VARCHAR);
        fractionName.setLabel("Name");
        fractionName.setWidth("200");
        result.addColumn(fractionName);

        // Add a column that links directly to the data object
        ExprColumn dataColumn = new ExprColumn(result, "Data",
                new SQLFragment("(SELECT MIN(d.RowId) FROM " + ExperimentService.get().getTinfoData() + " d, " +
                        MS2Manager.getTableInfoRuns() + " r WHERE d.Container = r.Container AND r.Run = " +
                        ExprColumn.STR_TABLE_ALIAS + ".Run AND d.DataFileURL = " + ExprColumn.STR_TABLE_ALIAS + ".mzxmlURL)"), Types.INTEGER);
        dataColumn.setFk(new ExpSchema(getUser(), getContainer()).getDataIdForeignKey());
        result.addColumn(dataColumn);

        ActionURL url = new ActionURL(MS2Controller.ShowRunAction.class, getContainer());
        result.getColumn("Run").setFk(new LookupForeignKey(url, "run", "Run", "Description")
        {
            public TableInfo getLookupTableInfo()
            {
                return new RunTableInfo(MS2Schema.this);
            }
        });

        return result;
    }

    public SequencesTableInfo createSequencesTable()
    {
        return new SequencesTableInfo(this);
    }

    public TableInfo createPeptidesTable(ContainerFilter containerFilter)
    {
        return new PeptidesTableInfo(this, true, containerFilter);
    }

    private ExpRunTable createSearchTable(String name, ContainerFilter filter, String... protocolObjectPrefix)
    {
        final ExpRunTable result = ExperimentService.get().createRunTable(name, this);
        result.setContainerFilter(filter);
        result.populate();
        String[] protocolPatterns = new String[protocolObjectPrefix.length];
        for (int i = 0; i < protocolObjectPrefix.length; i++)
        {
            protocolPatterns[i] = PROTOCOL_PATTERN_PREFIX + protocolObjectPrefix[i] + "%";
        }
        result.setProtocolPatterns(protocolPatterns);

        SQLFragment sql = new SQLFragment("(SELECT MIN(ms2Runs.run)\n" +
                "\nFROM " + MS2Manager.getTableInfoRuns() + " ms2Runs " +
                "\nWHERE ms2Runs.ExperimentRunLSID = " + ExprColumn.STR_TABLE_ALIAS + ".LSID AND ms2Runs.Deleted = ?)");
        sql.add(Boolean.FALSE);
        ColumnInfo ms2DetailsColumn = new ExprColumn(result, "MS2Details", sql, Types.INTEGER);
        ActionURL url = new ActionURL(MS2Controller.ShowRunAction.class, getContainer());
        ms2DetailsColumn.setFk(new LookupForeignKey(url, "run", "Run", "Description")
        {
            public TableInfo getLookupTableInfo()
            {
                FilteredTable result = new FilteredTable(MS2Manager.getTableInfoRuns());
                result.addWrapColumn(result.getRealTable().getColumn("Run"));
                result.addWrapColumn(result.getRealTable().getColumn("Description"));
                result.addWrapColumn(result.getRealTable().getColumn("Created"));
                result.addWrapColumn(result.getRealTable().getColumn("Path"));
                result.addWrapColumn(result.getRealTable().getColumn("SearchEngine"));
                result.addWrapColumn(result.getRealTable().getColumn("MassSpecType"));
                result.addWrapColumn(result.getRealTable().getColumn("PeptideCount"));
                result.addWrapColumn(result.getRealTable().getColumn("SpectrumCount"));
                result.addWrapColumn(result.getRealTable().getColumn("SearchEnzyme"));
                result.addWrapColumn(result.getRealTable().getColumn("Filename"));
                result.addWrapColumn(result.getRealTable().getColumn("Status"));
                result.addWrapColumn(result.getRealTable().getColumn("Type"));

                ColumnInfo iconColumn = result.wrapColumn("Links", result.getRealTable().getColumn("Run"));
                iconColumn.setDisplayColumnFactory(new DisplayColumnFactory()
                {
                    public DisplayColumn createRenderer(ColumnInfo colInfo)
                    {
                        ActionURL linkURL = new ActionURL(MS2Controller.ShowRunAction.class, getContainer());
                        return new IconDisplayColumn(colInfo, 18, 18, linkURL, "run", AppProps.getInstance().getContextPath() + "/MS2/images/runIcon.gif");
                    }
                });
                result.addColumn(iconColumn);
                return result;
            }
        });
        result.addColumn(ms2DetailsColumn);

        result.getColumn("Name").setDisplayColumnFactory(new DisplayColumnFactory()
        {
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                return new DataColumn(colInfo)
                {
                    private ColumnInfo _runCol;

                    @Override
                    public String renderURL(RenderContext ctx)
                    {
                        if (_runCol != null && _runCol.getValue(ctx) != null)
                        {
                            // In rare cases we'll have something that qualifies as an MS2 run
                            // based on its protocol LSID but that doesn't actually have a MS2 run
                            // attached to it in the database, so don't show links to it
                            return super.renderURL(ctx);
                        }
                        else
                        {
                            return null;
                        }
                    }

                    public void addQueryColumns(Set<ColumnInfo> columns)
                    {
                        super.addQueryColumns(columns);
                        FieldKey key = new FieldKey(FieldKey.fromString(getBoundColumn().getName()).getParent(),  "MS2Details");
                        Map<FieldKey, ColumnInfo> cols = QueryService.get().getColumns(getBoundColumn().getParentTable(), Collections.singleton(key));
                        _runCol = cols.get(key);
                        if (_runCol != null)
                        {
                            columns.add(_runCol);
                            ActionURL url = new ActionURL(MS2Controller.ShowRunAction.class, getContainer());
                            setURLExpression(new DetailsURL(url, Collections.singletonMap("run", _runCol.getFieldKey())));
                        }
                    }
                };
            }
        });

        ms2DetailsColumn.setHidden(false);

        //adjust the default visible columns
        List<FieldKey> columns = new ArrayList<FieldKey>(result.getDefaultVisibleColumns());
        columns.remove(FieldKey.fromParts("MS2Details"));
        columns.remove(FieldKey.fromParts("Protocol"));
        columns.remove(FieldKey.fromParts("CreatedBy"));

        columns.add(2, FieldKey.fromParts("MS2Details", "Links"));
        columns.add(FieldKey.fromParts("Input", "FASTA"));

        result.setDefaultVisibleColumns(columns);
        return result;
    }

    public void setRuns(MS2Run[] runs)
    {
        setRuns(Arrays.asList(runs));
    }

    public void setRuns(List<MS2Run> runs)
    {
        _runs = runs;
        _proteinGroupProteins.setRuns(_runs);
        Collections.sort(_runs, new Comparator<MS2Run>()
        {
            public int compare(MS2Run run1, MS2Run run2)
            {
                if (run1.getDescription() == null && run2.getDescription() == null)
                {
                    return 0;
                }
                if (run1.getDescription() == null)
                {
                    return 1;
                }
                if (run2.getDescription() == null)
                {
                    return -1;
                }
                return run1.getDescription().compareTo(run2.getDescription());
            }
        });
    }
    
    public List<MS2Run> getRuns()
    {
        return _runs;
    }

    protected SQLFragment getPeptideSelectSQL(SimpleFilter filter, Collection<FieldKey> fieldKeys)
    {
        return getSelectSQL(createPeptidesTable(ContainerFilter.EVERYTHING), filter, fieldKeys);
    }

    protected SQLFragment getSelectSQL(TableInfo tableInfo, SimpleFilter filter, Collection<FieldKey> fieldKeys)
    {
        Map<FieldKey, ColumnInfo> columnMap = QueryService.get().getColumns(tableInfo, fieldKeys);

        Collection<ColumnInfo> reqCols = new ArrayList<ColumnInfo>(columnMap.values());
        Set<String> unresolvedColumns = new HashSet<String>();
        reqCols = QueryService.get().ensureRequiredColumns(tableInfo, reqCols, filter, null, unresolvedColumns);

        SQLFragment innerSelect = Table.getSelectSQL(tableInfo, reqCols, null, null);

        Map<String, ColumnInfo> map = new HashMap<String, ColumnInfo>(reqCols.size());
        for(ColumnInfo col : reqCols)
        {
            map.put(col.getName(), col);
        }

        SQLFragment sql = new SQLFragment();
        sql.append("SELECT ");
        String separator = "";
        for (FieldKey fieldKey : fieldKeys)
        {
            sql.append(separator);
            separator = ", ";
            sql.append(columnMap.get(fieldKey).getAlias());
        }
        sql.append(" FROM (\n");
        sql.append(innerSelect);
        sql.append("\n) AS InnerSubquery ");

        sql.append(filter.getSQLFragment(getDbSchema().getSqlDialect(), map));
        return sql;
    }

    protected SQLFragment getPeptideSelectSQL(HttpServletRequest request, String viewName, Collection<FieldKey> fieldKeys)
    {
        QueryDefinition queryDef = QueryService.get().createQueryDefForTable(this, MS2Schema.HiddenTableType.PeptidesFilter.toString());
        SimpleFilter filter = new SimpleFilter();
        CustomView view = queryDef.getCustomView(getUser(), request, viewName);
        if (view != null)
        {
            ActionURL url = new ActionURL();
            view.applyFilterAndSortToURL(url, "InternalName");
            filter.addUrlFilters(url, "InternalName");
        }
        return getPeptideSelectSQL(filter, fieldKeys);
    }

    public CrosstabTableInfo createNormalizedProteinProphetComparisonTable(final MS2Controller.PeptideFilteringComparisonForm form, final ViewContext context)
    {
        VirtualTable rawTable;

        ColumnInfo normalizedIdCol = new ColumnInfo("NormalizedId");
        normalizedIdCol.setSqlTypeName("INT");
        normalizedIdCol.setHidden(true);

        ColumnInfo proteinGroupIdCol = new ColumnInfo("ProteinGroupId");
        proteinGroupIdCol.setSqlTypeName("INT");
        proteinGroupIdCol.setFk(new LookupForeignKey("RowId")
        {
            public TableInfo getLookupTableInfo()
            {
                return new ProteinGroupTableInfo(MS2Schema.this, true);
            }

            @Override
            public StringExpression getURL(ColumnInfo parent)
            {
                return getURL(parent, true);
            }
        });

        final String name;

        if (form != null && form.getRunList() != null)
        {
            try
            {
                name = ensureNormalizedProteinGroups(form.getRunList().intValue());
            }
            catch (SQLException e)
            {
                throw new RuntimeSQLException(e);
            }
        }
        else
        {
            name = "bogusTable";
        }

        rawTable = new VirtualTable(getDbSchema())
        {
            @NotNull
            @Override
            public SQLFragment getFromSQL()
            {
                return new SQLFragment("SELECT * FROM " + name);
            }
        };
        rawTable.setName(name);

        rawTable.addColumn(normalizedIdCol);
        normalizedIdCol.setParentTable(rawTable);
        rawTable.addColumn(proteinGroupIdCol);
        proteinGroupIdCol.setParentTable(rawTable);

        FilteredTable baseTable = new FilteredTable(rawTable);
        baseTable.wrapAllColumns(true);

        ExprColumn normalizedProteinCountCol = new ExprColumn(baseTable, "ProteinCount", new SQLFragment("(SELECT COUNT (DISTINCT SeqId) FROM " + name + " n,  " + MS2Manager.getTableInfoProteinGroupMemberships() + " pgm WHERE n.ProteinGroupId = pgm.ProteinGroupId and n.NormalizedId = " + ExprColumn.STR_TABLE_ALIAS + ".NormalizedId)"), Types.INTEGER);
        baseTable.addColumn(normalizedProteinCountCol);

        ColumnInfo proteinsCol = baseTable.wrapColumn("Proteins", rawTable.getColumn("NormalizedId"));
        proteinsCol.setHidden(false);
        baseTable.addColumn(proteinsCol);
        LookupForeignKey proteinsFK = new LookupForeignKey("NormalizedId")
        {
            public TableInfo getLookupTableInfo()
            {
                // Create a junction query that connects normalized group ids with protein identifications
                VirtualTable result = new VirtualTable(getDbSchema())
                {
                    @NotNull
                    @Override
                    public SQLFragment getFromSQL()
                    {
                        return new SQLFragment("SELECT DISTINCT NormalizedId, SeqId FROM " + name + " n, " + MS2Manager.getTableInfoProteinGroupMemberships() + " pgm WHERE n.ProteinGroupId = pgm.ProteinGroupId");
                    }
                };
                ColumnInfo normalizedIdCol = new ColumnInfo("NormalizedId", result);
                normalizedIdCol.setSqlTypeName("INT");
                result.addColumn(normalizedIdCol);
                ColumnInfo seqIdCol = new ColumnInfo("SeqId", result);
                seqIdCol.setSqlTypeName("INT");
                LookupForeignKey seqFK = new LookupForeignKey("SeqId")
                {
                    public TableInfo getLookupTableInfo()
                    {
                        return new SequencesTableInfo(MS2Schema.this);
                    }
                };
                seqFK.setPrefixColumnCaption(false);
                seqIdCol.setFk(seqFK);
                result.addColumn(seqIdCol);
                result.setName("InnerTable");

                return result;
            }

            @Override
            public StringExpression getURL(ColumnInfo parent)
            {
                return super.getURL(parent, true);
            }
        };
        proteinsCol.setFk(new MultiValuedForeignKey(proteinsFK, "SeqId"));

        TableInfo proteinGroupMembershipTable = createProteinGroupMembershipTable(form, context, false);
        ColumnInfo proteinGroupColumn = proteinGroupMembershipTable.getColumn("ProteinGroupId");

        SQLFragment selectSQL = QueryService.get().getSelectSQL(proteinGroupMembershipTable, Collections.singleton(proteinGroupColumn), null, null, Table.ALL_ROWS, 0);
        SQLFragment filterSQL = new SQLFragment("ProteinGroupId IN (SELECT " + proteinGroupColumn.getAlias() + " FROM (");
        filterSQL.append(selectSQL);
        filterSQL.append(") x)");

        baseTable.addCondition(filterSQL, "ProteinGroupId");

        CrosstabTableInfo result;
        CrosstabSettings settings = new CrosstabSettings(baseTable);
        CrosstabMeasure firstProteinGroupMeasure = settings.addMeasure(proteinGroupIdCol.getFieldKey(), CrosstabMeasure.AggregateFunction.MIN, "Run First Protein Group");
        CrosstabMeasure groupCountMeasure = settings.addMeasure(proteinGroupIdCol.getFieldKey(), CrosstabMeasure.AggregateFunction.COUNT, "Run Protein Group Count");
//        CrosstabMeasure groupsMeasure = settings.addMeasure(proteinGroupIdCol.getFieldKey(), CrosstabMeasure.AggregateFunction.GROUP_CONCAT, "Run Protein Groups");

        settings.setInstanceCountCaption("Found In Runs");
        settings.getRowAxis().setCaption("Normalized Protein Group");
        settings.getColumnAxis().setCaption("Runs");

        settings.getRowAxis().addDimension(normalizedIdCol.getFieldKey());
        settings.getRowAxis().addDimension(normalizedProteinCountCol.getFieldKey());
        settings.getRowAxis().addDimension(proteinsCol.getFieldKey());

        CrosstabDimension colDim = settings.getColumnAxis().addDimension(FieldKey.fromParts("ProteinGroupId", "ProteinProphetFileId", "Run"));
        colDim.setUrl(new ActionURL(MS2Controller.ShowRunAction.class, getContainer()).getLocalURIString() + "run=" + CrosstabMember.VALUE_TOKEN);

        if(null != _runs)
        {
            ArrayList<CrosstabMember> members = new ArrayList<CrosstabMember>();
            //build up the list of column members
            for (MS2Run run : _runs)
            {
                members.add(new CrosstabMember(Integer.valueOf(run.getRun()), colDim, run.getDescription()));
            }
            result = new CrosstabTableInfo(settings, members);
        }
        else
        {
            result = new CrosstabTableInfo(settings);
        }
        if (form != null)
        {
            result.setOrAggFitlers(form.isOrCriteriaForEachRun());
        }
        List<FieldKey> defaultCols = new ArrayList<FieldKey>();
        defaultCols.add(FieldKey.fromParts(CrosstabTableInfo.COL_INSTANCE_COUNT));
        defaultCols.add(FieldKey.fromParts(AggregateColumnInfo.getColumnName(null, firstProteinGroupMeasure), "Group"));
        defaultCols.add(FieldKey.fromParts(AggregateColumnInfo.getColumnName(null, groupCountMeasure)));
        defaultCols.add(FieldKey.fromParts(proteinsCol.getName(), "BestName"));
        result.setDefaultVisibleColumns(defaultCols);
        return result;
    }

    private static class NormalizedProteinGroupsTracker
    {
        private String _name;

        public NormalizedProteinGroupsTracker(String name)
        {
            _name = name;
        }

        public String getName()
        {
            return _name;
        }
    }

    private final static Map<Integer, NormalizedProteinGroupsTracker> NORMALIZED_PROTEIN_GROUP_CACHE = new HashMap<Integer, NormalizedProteinGroupsTracker>();

    private String ensureNormalizedProteinGroups(int runListId) throws SQLException
    {
        synchronized (NORMALIZED_PROTEIN_GROUP_CACHE)
        {
            if (NORMALIZED_PROTEIN_GROUP_CACHE.containsKey(runListId))
            {
                // See if we've already built a temp table
                return NORMALIZED_PROTEIN_GROUP_CACHE.get(runListId).getName();
            }

            Connection connection = MS2Manager.getSchema().getScope().getConnection();

            String shortName = "RunList" + runListId;
            String tempTableName = getDbSchema().getSqlDialect().getGlobalTempTablePrefix() + shortName;

            NormalizedProteinGroupsTracker tracker = new NormalizedProteinGroupsTracker(tempTableName);
            TempTableTracker.track(MS2Manager.getSchema(), tempTableName, tracker);
            try
            {
                // Populate the temp table with all of the protein groups from the selected runs
                SQLFragment insertSQL = new SQLFragment("SELECT x.RowId AS ProteinGroupId, x.RowId as NormalizedId INTO " + tempTableName +
                        " FROM (SELECT pg.RowId FROM " + MS2Manager.getTableInfoProteinGroups() + " pg, " +
                        MS2Manager.getTableInfoProteinProphetFiles() + " ppf WHERE pg.ProteinProphetFileId = ppf.RowId AND ppf.Run IN (");
                String separator = "";
                for (MS2Run run : getRuns())
                {
                    insertSQL.append(separator);
                    separator = ", ";
                    insertSQL.append(run.getRun());
                }
                insertSQL.append(") ) AS x");

                Table.execute(connection, insertSQL.getSQL(), new Object[0]);

                // Use the protein group's RowId as the normalized group id
                Table.execute(connection, "UPDATE " + tempTableName + " SET NormalizedId = ProteinGroupId", new Object[0]);

                // Figure out the minimum group id that contains a protein (SeqId) that's also in this group
                String updateSubQuery = "SELECT MIN(MinNormalizedId) AS NewNormalizedId, GroupId FROM \n" +
                        "(SELECT CASE WHEN g1.NormalizedId < g2.NormalizedId THEN g1.NormalizedId ELSE g2.NormalizedId END AS MinNormalizedId, g1.ProteinGroupId AS GroupId FROM " +
                        tempTableName + " g1, " + tempTableName + " g2, " + MS2Manager.getTableInfoProteinGroupMemberships() + " pg1, " +
                        MS2Manager.getTableInfoProteinGroupMemberships() + " pg2 WHERE pg1.SeqId = pg2.SeqId AND " +
                        "g2.ProteinGroupId = pg1.ProteinGroupId AND g1.ProteinGroupId = pg2.ProteinGroupId) Innermost GROUP BY GroupId)";

                String updateSQL = "UPDATE " + tempTableName + " SET NormalizedId = \n" +
                        "(SELECT NewNormalizedId FROM (" + updateSubQuery + " x \n" +
                        "WHERE GroupId = ProteinGroupId) WHERE NormalizedId != (SELECT NewNormalizedId FROM (" + updateSubQuery + " x WHERE GroupId = ProteinGroupId)";

                int rowsUpdated;
                do
                {
                    // Set the normalized group id to be the minimum id from all the groups that share the same proteins
                    rowsUpdated = Table.execute(connection, updateSQL, new Object[0]);
                }
                // Keep going while any value changed. When we're done, we've found the transitive closure and any
                // groups that share proteins (including transitively) are lumped into the same normalized group
                while (rowsUpdated > 0);
                NORMALIZED_PROTEIN_GROUP_CACHE.put(runListId, tracker);
            }
            finally
            {
                if (connection != null) { try { connection.close(); } catch (SQLException e) {} }
            }
            return tempTableName;
        }
    }


    public CrosstabTableInfo createProteinProphetCrosstabTable(MS2Controller.PeptideFilteringComparisonForm form, ViewContext context)
    {
        // Don't need to filter by run since we'll add a filter, post-join
        FilteredTable baseTable = createProteinGroupMembershipTable(form, context, false);

        CrosstabSettings settings = new CrosstabSettings(baseTable);
        SimpleFilter filter = new SimpleFilter();
        List<Integer> runIds = new ArrayList<Integer>();
        if (_runs != null)
        {
            for (MS2Run run : _runs)
            {
                runIds.add(run.getRun());
            }
        }
        filter.addClause(new SimpleFilter.InClause("ProteinGroupId/ProteinProphetFileId/Run", runIds, false));
        settings.setSourceTableFilter(filter);

        CrosstabDimension rowDim = settings.getRowAxis().addDimension(FieldKey.fromParts("SeqId"));
        ActionURL showProteinURL = new ActionURL(MS2Controller.ShowProteinAction.class, getContainer());
        rowDim.setUrl(showProteinURL.getLocalURIString() + "&seqId=${SeqId}");
        rowDim.getSourceColumn().setDisplayColumnFactory(new DisplayColumnFactory(){
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                DisplayColumn dc = new DataColumn(colInfo);
                dc.setLinkTarget("prot");
                return dc;
            }
        });

        CrosstabDimension colDim = settings.getColumnAxis().addDimension(FieldKey.fromParts("ProteinGroupId", "ProteinProphetFileId", "Run"));
        colDim.setUrl(new ActionURL(MS2Controller.ShowRunAction.class, getContainer()).getLocalURIString() + "run=" + CrosstabMember.VALUE_TOKEN);

        CrosstabMeasure proteinGroupMeasure = settings.addMeasure(FieldKey.fromParts("ProteinGroupId"), CrosstabMeasure.AggregateFunction.MIN, "Protein Group");

        settings.setInstanceCountCaption("Num Runs");
        settings.getRowAxis().setCaption("Protein Information");
        settings.getColumnAxis().setCaption("Runs");

        CrosstabTableInfo result;

        if(null != _runs)
        {
            ArrayList<CrosstabMember> members = new ArrayList<CrosstabMember>();
            //build up the list of column members
            for (MS2Run run : _runs)
            {
                members.add(new CrosstabMember(Integer.valueOf(run.getRun()), colDim, run.getDescription()));
            }
            result = new CrosstabTableInfo(settings, members);
        }
        else
        {
            result = new CrosstabTableInfo(settings);
        }
        if (form != null)
        {
            result.setOrAggFitlers(form.isOrCriteriaForEachRun());
        }
        List<FieldKey> defaultCols = new ArrayList<FieldKey>();
        defaultCols.add(FieldKey.fromParts("SeqId"));
        defaultCols.add(FieldKey.fromParts(CrosstabTableInfo.COL_INSTANCE_COUNT));
        defaultCols.add(FieldKey.fromParts(proteinGroupMeasure.getName(), "Group"));
        defaultCols.add(FieldKey.fromParts(proteinGroupMeasure.getName(), "GroupProbability"));
        result.setDefaultVisibleColumns(defaultCols);
        return result;
    }
}
