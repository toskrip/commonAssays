package org.labkey.flow.query;

import org.labkey.api.data.*;
import org.labkey.flow.persist.FlowManager;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;

import java.util.Collection;

abstract public class AttributeForeignKey<T> implements ForeignKey
{
    public StringExpressionFactory.StringExpression getURL(ColumnInfo parent)
    {
        return null;
    }

    public AttributeForeignKey()
    {
    }

    public TableInfo getLookupTableInfo()
    {
        VirtualTable ret = new VirtualTable(FlowManager.get().getSchema());
        for (T attrName : getAttributes())
        {
            ColumnInfo column = new ColumnInfo(attrName.toString(), ret);
            initColumn(attrName, column);
            ret.addColumn(column);
        }
        return ret;
    }

    public ColumnInfo createLookupColumn(ColumnInfo parent, String displayField)
    {
        if (displayField == null)
            return null;

        T attrName = attributeFromString(displayField);
        if (attrName == null)
            return null;
        int attrId = FlowManager.get().getAttributeId(attrName.toString());
        SQLFragment sql = sqlValue(parent, attrName, attrId);
        ExprColumn ret = new ExprColumn(parent.getParentTable(), new FieldKey(FieldKey.fromString(parent.getName()), displayField).toString(), sql, 0, parent);
        initColumn(attrName, ret);
        return ret;
    }

    abstract protected Collection<T> getAttributes();
    abstract protected SQLFragment sqlValue(ColumnInfo objectIdColumn, T attrName, int attrId);
    abstract protected void initColumn(T attrName, ColumnInfo column);
    abstract protected T attributeFromString(String field);
}
