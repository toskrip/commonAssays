/*
 * Copyright (c) 2006-2011 LabKey Corporation
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

package org.labkey.flow.query;

import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.data.*;
import org.labkey.api.util.StringExpression;
import org.labkey.flow.data.AttributeType;
import org.labkey.flow.view.GraphColumn;

import org.labkey.flow.analysis.web.GraphSpec;
import org.labkey.flow.analysis.web.SubsetSpec;

import java.util.Collection;

public class GraphForeignKey extends AttributeForeignKey<GraphSpec>
{
    FlowPropertySet _fps;

    public GraphForeignKey(Container c, FlowPropertySet fps)
    {
        super(c);
        _fps = fps;
    }

    @Override
    protected AttributeType type()
    {
        return AttributeType.graph;
    }

    protected Collection<GraphSpec> getAttributes()
    {
        return _fps.getGraphProperties().keySet();
    }

    protected GraphSpec attributeFromString(String field)
    {
        try
        {
            return new GraphSpec(field);
        }
        catch (IllegalArgumentException e)
        {
            return null;
        }
    }

    protected void initColumn(final GraphSpec spec, ColumnInfo column)
    {
        column.setSqlTypeName("INTEGER");
        SubsetSpec subset = _fps.simplifySubset(spec.getSubset());
        GraphSpec captionSpec = new GraphSpec(subset, spec.getParameters());
        column.setLabel(captionSpec.toString());
        column.setFk(new AbstractForeignKey() {
            public ColumnInfo createLookupColumn(ColumnInfo parent, String displayField)
            {
                if (displayField != null)
                    return null;
                SQLFragment sqlExpr = new SQLFragment();
                sqlExpr.appendStringLiteral(spec.toString());
                return new ExprColumn(parent.getParentTable(), new FieldKey(parent.getFieldKey(),"$"), sqlExpr, JdbcType.VARCHAR);
            }

            public TableInfo getLookupTableInfo()
            {
                return null;
            }

            public StringExpression getURL(ColumnInfo parent)
            {
                return null;
            }
        });
        column.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                return new GraphColumn(colInfo);
            }
        });
    }

    protected SQLFragment sqlValue(ColumnInfo objectIdColumn, GraphSpec attrName, int attrId)
    {
        SQLFragment sql = new SQLFragment("(SELECT CASE WHEN flow.Graph.RowId IS NOT NULL THEN ");
        sql.append(objectIdColumn.getValueSql(ExprColumn.STR_TABLE_ALIAS));
        sql.append(" END");
        sql.append("\nFROM flow.Graph WHERE flow.Graph.GraphId = ");
        sql.append(attrId);
        sql.append("\nAND flow.Graph.ObjectId = ");
        sql.append(objectIdColumn.getValueSql(ExprColumn.STR_TABLE_ALIAS));
        sql.append(")");

        //SQLFragment sql = new SQLFragment("(SELECT CASE WHEN flow.Graph.RowId IS NOT NULL THEN ");
        //sql.append(objectIdColumn.getValueSql(ExprColumn.STR_TABLE_ALIAS));
        //sql.append(" END");
        //sql.append("\nFROM flow.Graph WHERE flow.Graph.GraphId = flow.GraphAttr.Id AND flow.GraphAttr.Name = ?");
        //sql.add(attrName);
        //sql.append("\nAND flow.Graph.ObjectId = ");
        //sql.append(objectIdColumn.getValueSql(ExprColumn.STR_TABLE_ALIAS));
        //sql.append(")");
        return sql;
    }
}
