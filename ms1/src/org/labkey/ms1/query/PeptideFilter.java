/*
 * Copyright (c) 2008 LabKey Corporation
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
package org.labkey.ms1.query;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlDialect;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Used to filter for a given set of peptide sequences
 *
 * Created by IntelliJ IDEA.
 * User: Dave
 * Date: Jan 14, 2008
 * Time: 10:17:45 AM
 */
public class PeptideFilter extends SimpleFilter.FilterClause implements FeaturesFilter
{
    private String[] _sequences;
    private boolean _exact = false;

    public PeptideFilter(String sequenceList, boolean exact)
    {
        _sequences = sequenceList.split(",");
        _exact = exact;
    }

    public PeptideFilter(String[] sequences, boolean exact)
    {
        _sequences = sequences;
        _exact = exact;
    }

    private String normalizeSequence(String sequence)
    {
        //strip off the bits outside the first and last .
        //and remove all non-alpha characters
        char[] trimmed = new char[sequence.length()];
        int len = 0;
        char ch;

        for(int idx = Math.max(0, sequence.indexOf('.') + 1);
            idx < sequence.length() && sequence.charAt(idx) != '.';
            ++idx)
        {
            ch = sequence.charAt(idx);
            if((ch >= 'A' && ch <= 'Z') || '?' == ch || '*' == ch) //allow wildcards
            {
                //translate user wildcards to SQL
                if('?' == ch)
                    ch = '_';
                if('*' == ch)
                    ch = '%';
                
                trimmed[len] = ch;
                ++len;
            }
        }
        
        return new String(trimmed, 0, len);
    }

    public List<String> getColumnNames()
    {
        if(_exact)
            return Arrays.asList("TrimmedPeptide", "Peptide");
        else
            return Arrays.asList("TrimmedPeptide");
    }

    public SQLFragment toSQLFragment(Map<String, ? extends ColumnInfo> columnMap, SqlDialect dialect)
    {
        if(null == _sequences)
            return null;

        // OR together the sequence conditions
        SQLFragment sql = new SQLFragment();
        for(int idx = 0; idx < _sequences.length; ++idx)
        {
            if(idx > 0)
                sql.append(" OR ");

            sql.append(genSeqPredicate(_sequences[idx], null));
        }
        return sql;
    }

    public SQLFragment getWhereClause(Map<String, String> aliasMap, SqlDialect dialect)
    {
        if (null == _sequences)
            return null;

        String pepDataAlias = aliasMap.get("ms2.PeptidesData");
        assert(null != pepDataAlias);

        // OR together the sequence conditions
        SQLFragment sql = new SQLFragment();
        for (int idx = 0; idx < _sequences.length; ++idx)
        {
            if (idx > 0)
                sql.append(" OR ");

            sql.append(genSeqPredicate(_sequences[idx], pepDataAlias));
        }
        return sql;
    }

    private SQLFragment genSeqPredicate(String sequence, String pepDataAlias)
    {
        //force sequence to upper-case for case-sensitive DBs like PostgreSQL
        sequence = sequence.toUpperCase();
        
        //always add a condition for pd.TrimmedPeptide using normalized version of sequence
        SQLFragment sql = new SQLFragment(null == pepDataAlias ? "(TrimmedPeptide"
                : "(" + pepDataAlias + ".TrimmedPeptide");

        if (_exact)
        {
            sql.append("=?");
            sql.add(normalizeSequence(sequence));
        }
        else
        {
            sql.append(" LIKE ?");
            sql.add(normalizeSequence(sequence) + '%');
        }

        //if _exact, AND another contains condition against pd.Peptide
        if(_exact)
        {
            sql.append(null == pepDataAlias ? " AND Peptide LIKE ?" : " AND " + pepDataAlias + ".Peptide LIKE ?");
            sql.add('%' + sequence.replace("'", "''").trim() + '%'); //FIX: 6679
        }

        sql.append(")");

        return sql;
    }
}
