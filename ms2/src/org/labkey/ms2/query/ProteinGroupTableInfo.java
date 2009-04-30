/*
 * Copyright (c) 2007-2009 LabKey Corporation
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

import org.labkey.api.query.*;
import org.labkey.api.data.*;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.security.ACL;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.ms2.*;
import org.labkey.ms2.protein.ProteinManager;

import java.util.*;
import java.sql.Types;

/**
 * User: jeckels
 * Date: Feb 8, 2007
 */
public class ProteinGroupTableInfo extends FilteredTable
{
    private static final Set<String> HIDDEN_PROTEIN_GROUP_COLUMN_NAMES = new CaseInsensitiveHashSet(Arrays.asList("RowId", "GroupNumber", "IndistinguishableCollectionId", "Deleted", "HasPeptideProphet"));
    private static final Set<String> HIDDEN_PROTEIN_GROUP_MEMBERSHIPS_COLUMN_NAMES = new CaseInsensitiveHashSet("ProteinGroupId", "SeqId");
    private final MS2Schema _schema;
    private List<MS2Run> _runs;

    public ProteinGroupTableInfo(MS2Schema schema)
    {
        this(schema, true);
    }

    public ProteinGroupTableInfo(MS2Schema schema, boolean includeFirstProteinColumn)
    {
        super(MS2Manager.getTableInfoProteinGroups());
        _schema = schema;


        ColumnInfo groupNumberColumn = wrapColumn("Group", getRealTable().getColumn("GroupNumber"));
        groupNumberColumn.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                return new GroupNumberDisplayColumn(colInfo, _schema.getContainer());
            }
        });

        addColumn(groupNumberColumn);

        wrapAllColumns(true);
        addColumn(wrapColumn("ProteinProphet", getRealTable().getColumn("ProteinProphetFileId")));
        getColumn("ProteinProphetFileId").setIsHidden(true);

        ColumnInfo quantitation = wrapColumn("Quantitation", getRealTable().getColumn("RowId"));
        quantitation.setIsUnselectable(true);
        quantitation.setFk(new LookupForeignKey("ProteinGroupId")
        {
            public TableInfo getLookupTableInfo()
            {
                return new ProteinQuantitationTable();
            }
        });
        quantitation.setKeyField(false);
        addColumn(quantitation);

        for (ColumnInfo col : getColumns())
        {
            if (HIDDEN_PROTEIN_GROUP_COLUMN_NAMES.contains(col.getName()))
            {
                col.setIsHidden(true);
            }
        }

        LookupForeignKey foreignKey = new LookupForeignKey("RowId")
        {
            public TableInfo getLookupTableInfo()
            {
                return new ProteinProphetFileTableInfo(_schema);
            }
        };
        foreignKey.setPrefixColumnCaption(false);
        getColumn("ProteinProphetFileId").setFk(foreignKey);
        getColumn("ProteinProphet").setFk(foreignKey);

        if (includeFirstProteinColumn)
        {
            SQLFragment firstProteinSQL = new SQLFragment();
            firstProteinSQL.append("SELECT s.SeqId FROM ");
            firstProteinSQL.append(MS2Manager.getTableInfoProteinGroupMemberships(), "pgm");
            firstProteinSQL.append(", ");
            firstProteinSQL.append(ProteinManager.getTableInfoSequences(), "s");
            firstProteinSQL.append(" WHERE s.SeqId = pgm.SeqId AND pgm.ProteinGroupId = ");
            firstProteinSQL.append(ExprColumn.STR_TABLE_ALIAS);
            firstProteinSQL.append(".RowId ORDER BY s.Length, s.BestName");
            ProteinManager.getSqlDialect().limitRows(firstProteinSQL, 1);
            firstProteinSQL.insert(0, "(");
            firstProteinSQL.append(")");

            ExprColumn firstProteinColumn = new ExprColumn(this, "FirstProtein", firstProteinSQL, Types.INTEGER);
            firstProteinColumn.setFk(new LookupForeignKey("SeqId")
            {
                public TableInfo getLookupTableInfo()
                {
                    return new SequencesTableInfo(null, _schema);
                }
            });
            addColumn(firstProteinColumn);
        }

        SQLFragment proteinCountSQL = new SQLFragment();
        proteinCountSQL.append("(SELECT COUNT(SeqId) FROM ");
        proteinCountSQL.append(MS2Manager.getTableInfoProteinGroupMemberships());
        proteinCountSQL.append(" WHERE ProteinGroupId = ");
        proteinCountSQL.append(ExprColumn.STR_TABLE_ALIAS);
        proteinCountSQL.append(".RowId)");
        ExprColumn proteinCountColumn = new ExprColumn(this, "ProteinCount", proteinCountSQL, Types.INTEGER);
        addColumn(proteinCountColumn);

        List<FieldKey> defaultColumns = new ArrayList<FieldKey>();
        defaultColumns.add(FieldKey.fromParts("Group"));
        defaultColumns.add(FieldKey.fromParts("GroupProbability"));
        defaultColumns.add(FieldKey.fromParts("ErrorRate"));
        defaultColumns.add(FieldKey.fromParts("UniquePeptidesCount"));
        defaultColumns.add(FieldKey.fromParts("TotalNumberPeptides"));

        setDefaultVisibleColumns(defaultColumns);
    }

    public void addPeptideFilter(MS2Controller.ProteinSearchForm form, ViewContext context)
    {
        if (form.isNoPeptideFilter())
        {
            return;
        }

        SQLFragment peptidesSQL;

        Set<FieldKey> peptideFieldKeys = Collections.singleton(FieldKey.fromParts("RowId"));
        if (form.isCustomViewPeptideFilter())
        {
            peptidesSQL = _schema.getPeptideSelectSQL(context.getRequest(), form.getCustomViewName(context), peptideFieldKeys);
        }
        else
        {
            SimpleFilter filter = new SimpleFilter();
            if (form.isPeptideProphetFilter() && form.getPeptideProphetProbability() != null)
            {
                filter.addClause(new CompareType.CompareClause("PeptideProphet", CompareType.GTE, form.getPeptideProphetProbability()));
            }
            peptidesSQL = _schema.getPeptideSelectSQL(filter, peptideFieldKeys);
        }
        SQLFragment condition = new SQLFragment();
        condition.append("RowId IN (SELECT ProteinGroupId FROM " + MS2Manager.getTableInfoPeptideMemberships() + " WHERE PeptideId IN (");
        condition.append(peptidesSQL);
        condition.append("))");
        addCondition(condition, "RowId");
    }

    public void addProteinsColumn()
    {
        ColumnInfo proteinGroup = wrapColumn("Proteins", getRealTable().getColumn("RowId"));
        LookupForeignKey fk = new LookupForeignKey("ProteinGroupId")
        {
            public TableInfo getLookupTableInfo()
            {
                TableInfo info = MS2Manager.getTableInfoProteinGroupMemberships();
                FilteredTable result = new FilteredTable(info);
                for (ColumnInfo col : info.getColumns())
                {
                    ColumnInfo newColumn = result.addWrapColumn(col);
                    if (HIDDEN_PROTEIN_GROUP_MEMBERSHIPS_COLUMN_NAMES.contains(newColumn.getName()))
                    {
                        newColumn.setIsHidden(true);
                    }
                }

                ColumnInfo proteinColumn = result.wrapColumn("Protein", info.getColumn("SeqId"));
                proteinColumn.setDisplayColumnFactory(new DisplayColumnFactory()
                {
                    public DisplayColumn createRenderer(ColumnInfo colInfo)
                    {
                        DataColumn result = new DataColumn(colInfo);
                        result.setLinkTarget("prot");

                        ActionURL url = new ActionURL(MS2Controller.ShowProteinAction.class, _schema.getContainer());
                        if (_runs != null && _runs.size() == 1)
                        {
                            url.addParameter("run", Integer.toString(_runs.get(0).getRun()));
                        }
                        result.setURL(url.getLocalURIString() + "&proteinGroupId=${RowId}&seqId=${" + colInfo.getAlias() + "}");
                        return result;
                    }
                });
                result.addColumn(proteinColumn);

                proteinColumn.setFk(new LookupForeignKey("SeqId", "DatabaseSequenceName")
                {
                    public TableInfo getLookupTableInfo()
                    {
                        SequencesTableInfo result = new SequencesTableInfo(null, _schema);
                        SQLFragment sql = new SQLFragment();
                        sql.append("(SELECT Min(LookupString) FROM ");
                        sql.append(ProteinManager.getTableInfoFastaSequences(), "fs");
                        sql.append(", ");
                        sql.append(MS2Manager.getTableInfoRuns(), "r");
                        sql.append(", ");
                        sql.append(MS2Manager.getTableInfoProteinProphetFiles(), "ppf");
                        sql.append("\nWHERE fs.SeqId = ");
                        sql.append(ExprColumn.STR_TABLE_ALIAS);
                        sql.append(".SeqId AND fs.FastaId = r.FastaId AND r.Run = ppf.Run AND ppf.RowId = ");
                        sql.append(ProteinGroupTableInfo.this.getColumn("ProteinProphetFileId").getValueSql());
                        sql.append(")");
                        ExprColumn col = new ExprColumn(result, "DatabaseSequenceName", sql, Types.VARCHAR);

                        result.addColumn(col);
                        return result;
                    }
                });
                return result;
            }
        };
        fk.setPrefixColumnCaption(false);
        proteinGroup.setFk(fk);
        proteinGroup.setKeyField(false);
        addColumn(proteinGroup);
    }

    public void addProteinDetailColumns()
    {
        ColumnInfo rowIdColumn = _rootTable.getColumn("RowId");

        DisplayColumnFactory factory = new DisplayColumnFactory()
        {
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                _schema.getProteinGroupProteins().setRuns(_schema.getRuns());
                ProteinListDisplayColumn result = new ProteinListDisplayColumn(colInfo.getColumnName(), _schema.getProteinGroupProteins());
                result.setColumnInfo(colInfo);
                return result;
            }
        };

        ColumnInfo proteinNameColumn = wrapColumn("Protein", rowIdColumn);
        proteinNameColumn.setDisplayColumnFactory(factory);
        addColumn(proteinNameColumn);

        ColumnInfo bestNameColumn = wrapColumn("BestName", rowIdColumn);
        bestNameColumn.setDisplayColumnFactory(factory);
        addColumn(bestNameColumn);

        ColumnInfo bestGeneNameColumn = wrapColumn("BestGeneName", rowIdColumn);
        bestGeneNameColumn.setDisplayColumnFactory(factory);
        addColumn(bestGeneNameColumn);

        ColumnInfo massColumn = wrapColumn("SequenceMass", rowIdColumn);
        massColumn.setDisplayColumnFactory(factory);
        addColumn(massColumn);

        ColumnInfo descriptionColumn = wrapColumn("Description", rowIdColumn);
        descriptionColumn.setDisplayColumnFactory(factory);
        addColumn(descriptionColumn);


        ColumnInfo totalCount = wrapColumn("TotalFilteredPeptides", rowIdColumn);
        totalCount.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                ColumnInfo peptideColumn = colInfo.getParentTable().getColumn("Peptide");
                return new PeptideCountCoverageColumn(colInfo, peptideColumn, "TotalFilteredPeptides");
            }
        });
        addColumn(totalCount);

        ColumnInfo uniqueCount = wrapColumn("UniqueFilteredPeptides", rowIdColumn);
        uniqueCount.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                ColumnInfo peptideColumn = colInfo.getParentTable().getColumn("Peptide");
                return new UniquePeptideCountCoverageColumn(colInfo, peptideColumn, "UniqueFilteredPeptides");
            }
        });
        addColumn(uniqueCount);
    }

    public void addContainerCondition(Container c, User u, boolean includeSubfolders)
    {
        SQLFragment sql = new SQLFragment();
        sql.append("ProteinProphetFileId IN (SELECT ppf.RowId FROM ");
        sql.append(MS2Manager.getTableInfoProteinProphetFiles(), "ppf");
        sql.append(", ");
        sql.append(MS2Manager.getTableInfoRuns(), "r");
        sql.append(" WHERE ppf.run = r.run AND r.Deleted = ? AND r.Container IN ");
        sql.add(Boolean.FALSE);
        if (includeSubfolders)
        {
            Set<Container> containers = ContainerManager.getAllChildren(c, u, ACL.PERM_READ);
            sql.append(ContainerManager.getIdsAsCsvList(new HashSet<Container>(containers)));
        }
        else
        {
            sql.append("('");
            sql.append(c.getId());
            sql.append("')");
        }
        sql.append(")");
        addCondition(sql);
    }

    public void addSeqIdFilter(Integer[] seqIds)
    {
        SQLFragment sql = new SQLFragment();
        sql.append("RowId IN (\n");
        sql.append("SELECT ProteinGroupId FROM ");
        sql.append(MS2Manager.getTableInfoProteinGroupMemberships(), "pgm");
        sql.append(" WHERE pgm.SeqId IN (\n");
        if (seqIds.length == 0)
        {
            sql.append("NULL");
        }
        else
        {
            String separator = "";
            for (long seqId : seqIds)
            {
                sql.append(separator);
                separator = ", ";
                sql.append(seqId);
            }
        }
        sql.append("))");

        addCondition(sql);
    }
    
    public void addProteinNameFilter(String identifier, boolean exactMatch)
    {
        List<String> params = SequencesTableInfo.getIdentifierParameters(identifier);
        SQLFragment sql = new SQLFragment();
        sql.append("RowId IN (\n");
        sql.append("SELECT ProteinGroupId FROM ");
        sql.append(MS2Manager.getTableInfoProteinGroupMemberships(), "pgm");
        sql.append(" WHERE pgm.SeqId IN (\n");
        sql.append("SELECT SeqId FROM ");
        sql.append(ProteinManager.getTableInfoAnnotations(), "a");
        sql.append(" WHERE ");
        sql.append(SequencesTableInfo.getIdentifierClause(params, "a.AnnotVal", exactMatch));
        sql.append("\n");
        sql.append("UNION\n");
        sql.append("SELECT SeqId FROM ");
        sql.append(ProteinManager.getTableInfoFastaSequences(), "fs");
        sql.append(" WHERE ");
        sql.append(SequencesTableInfo.getIdentifierClause(params, "fs.LookupString", exactMatch));
        sql.append("\n");
        sql.append("UNION\n");
        sql.append("SELECT SeqId FROM ");
        sql.append(ProteinManager.getTableInfoIdentifiers(), "i");
        sql.append(" WHERE ");
        sql.append(SequencesTableInfo.getIdentifierClause(params, "i.Identifier", exactMatch));
        sql.append("\n");
        sql.append("))");
        addCondition(sql);
    }

    public void addMinimumProbability(float minProb)
    {
        SQLFragment sql = new SQLFragment();
        sql.append("GroupProbability >= ?");
        sql.add(minProb);
        addCondition(sql);
    }

    public void addMaximumErrorRate(float maxError)
    {
        SQLFragment sql = new SQLFragment();
        sql.append("ErrorRate <= ?");
        sql.add(maxError);
        addCondition(sql);
    }

    public void setRunFilter(List<MS2Run> runs)
    {
        _runs = runs;
        SQLFragment sql = new SQLFragment();
        sql.append("ProteinProphetFileId IN (SELECT RowId FROM ");
        sql.append(MS2Manager.getTableInfoProteinProphetFiles());
        sql.append(" WHERE Run IN (SELECT Run FROM ");
        sql.append(MS2Manager.getTableInfoRuns());
        sql.append(" WHERE Container = ? AND Deleted = ?");
        sql.add(_schema.getContainer().getId());
        sql.add(Boolean.FALSE);
        if (runs != null)
        {
            assert !runs.isEmpty() : "Doesn't make sense to filter to no runs";
            sql.append(" AND Run IN (");
            String separator = "";
            for (MS2Run run : runs)
            {
                sql.append(separator);
                sql.append(run.getRun());
                separator = ", ";
            }
            sql.append(")");
        }
        sql.append("))");
        addCondition(sql);

    }
}
