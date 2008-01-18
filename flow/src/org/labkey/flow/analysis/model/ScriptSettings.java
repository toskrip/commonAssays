package org.labkey.flow.analysis.model;

import org.fhcrc.cpas.flow.script.xml.CriteriaDef;
import org.fhcrc.cpas.flow.script.xml.ParameterDef;
import org.fhcrc.cpas.flow.script.xml.SettingsDef;
import org.fhcrc.cpas.flow.script.xml.FilterDef;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class ScriptSettings implements Serializable
{
    Map<String, ParameterInfo> _parameters = new HashMap();
    Map<String, SampleCriteria> _criteria = new HashMap<String, SampleCriteria>();

    static public class ParameterInfo implements Serializable
    {
        String _name;
        Double _minValue;

        ParameterInfo(String name)
        {
            _name = name;
        }

        public String getName()
        {
            return _name;
        }

        public Double getMinValue()
        {
            return _minValue;
        }

        public void setMinValue(double value)
        {
            _minValue = value;
        }

        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ParameterInfo that = (ParameterInfo) o;

            if (_minValue != null ? !_minValue.equals(that._minValue) : that._minValue != null) return false;
            if (!_name.equals(that._name)) return false;

            return true;
        }

        public int hashCode()
        {
            int result;
            result = _name.hashCode();
            result = 31 * result + (_minValue != null ? _minValue.hashCode() : 0);
            return result;
        }
    }

    public ParameterInfo getParameterInfo(String name, boolean create)
    {
        ParameterInfo ret = _parameters.get(name);
        if (ret != null)
            return ret;
        if (!create)
            return null;
        ret = new ParameterInfo(name);
        _parameters.put(name, ret);
        return ret;
    }

    public SampleCriteria getSampleCriteria(String keyword)
    {
        return _criteria.get(keyword);
    }

    public Collection<SampleCriteria> getSampleCriteria()
    {
        return _criteria.values();
    }

    public void merge(ScriptSettings that)
    {
        if (that == null)
            return;
        for (ParameterInfo paramInfo : that._parameters.values())
        {
            ParameterInfo mine = getParameterInfo(paramInfo.getName(), true);
            if (paramInfo.getMinValue() != null)
            {
                mine.setMinValue(paramInfo.getMinValue());
            }
        }

        for (SampleCriteria criteria : that._criteria.values())
        {
            SampleCriteria newCriteria = new SampleCriteria();
            newCriteria.setKeyword(criteria.getKeyword());
            newCriteria.setPattern(criteria.getPattern());
            _criteria.put(newCriteria.getKeyword(), newCriteria);
        }
    }

    public void merge(SettingsDef settings)
    {
        if (settings == null)
            return;
        for (ParameterDef param : settings.getParameterArray())
        {
            ParameterInfo mine = getParameterInfo(param.getName(), true);
            if (param.isSetMinValue())
            {
                mine.setMinValue(param.getMinValue());
            }
        }

        FilterDef filter = settings.getFilter();
        if (filter != null)
        {
            for (CriteriaDef criteria : filter.getCriteriaArray())
            {
                SampleCriteria mine = getSampleCriteria(criteria.getKeyword());
                if (mine == null)
                {
                    SampleCriteria sampleCriteria = SampleCriteria.fromCriteriaDef(criteria);
                    _criteria.put(criteria.getKeyword(), sampleCriteria);
                }
            }
        }
    }

    public static ScriptSettings fromSettingsDef(SettingsDef settings)
    {
        ScriptSettings ret = new ScriptSettings();
        ret.merge(settings);
        return ret;
    }

    public SettingsDef toSettingsDef()
    {
        SettingsDef ret = SettingsDef.Factory.newInstance();
        for (ParameterInfo info : _parameters.values())
        {
            ParameterDef paramDef = ret.addNewParameter();
            paramDef.setName(info.getName());
            if (info.getMinValue() != null)
            {
                paramDef.setMinValue(info.getMinValue());
            }
        }

        if (_criteria.size() > 0)
        {
            FilterDef filterDef = ret.addNewFilter();
            for (SampleCriteria criteria : _criteria.values())
            {
                CriteriaDef criteriaDef = filterDef.addNewCriteria();
                criteriaDef.setKeyword(criteria.getKeyword());
                criteriaDef.setPattern(criteria.getPattern());
            }
        }
        return ret;
    }

    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ScriptSettings that = (ScriptSettings) o;

        if (!_parameters.equals(that._parameters)) return false;
        if (!_criteria.equals(that._criteria)) return false;

        return true;
    }

    public int hashCode()
    {
        int result;
        result = _parameters.hashCode();
        result = 31 * result + _criteria.hashCode();
        return result;
    }
}
