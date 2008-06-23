<%
/*
 * Copyright (c) 2006-2008 LabKey Corporation
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
%>
<%@ page import="org.labkey.flow.analysis.web.SubsetSpec" %>
<%@ page import="org.labkey.flow.controllers.editscript.ScriptController"%>
<%@ page import="java.util.Collection" %>
<%@ page import="java.util.Map" %>
<%@ page extends="org.labkey.flow.controllers.editscript.ScriptController.Page" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%=pageHeader(ScriptController.Action.editAnalysis)%>
<% ScriptController.AnalysisForm bean = (ScriptController.AnalysisForm) form; %>
<%Map<String, String> params = form.getParameters();
    Collection<SubsetSpec> subsets = form.analysisScript.getSubsets();
%>
<script>
    function addStat()
    {
        var subset = getValue(document.getElementById("stat_subset"));
        var stat = getValue(document.getElementById("stat_stat"));
        var parameter = getValue(document.getElementById("stat_parameter"));

        var statistic = "";

        if (stat == "Median" || stat == "Mean" || stat == "Std_Dev" || stat == "Percentile" || stat == "CV")
        {
            if (!parameter)
            {
                alert("You must specify a parameter for the statistic '" + stat + "'");
                return;
            }
            if (subset)
            {
                statistic = subset + ":";
            }
            if (stat == "Percentile")
            {
                var percentile = getValue(document.getElementById("stat_parameter2"));
                var num = new Number(percentile);
                if (!(num >= 0 && num <= 100))
                {
                    alert(percentile + " needs to be a number between 0 and 100");
                    document.getElementById('stat_parameter2').focus();
                    return;
                }
                parameter += ":" + percentile;
            }

            statistic += stat + "(" + parameter + ")";
        }
        else
        {
            if (subset)
            {
                statistic = subset + ":";
            }
            else
            {
                if (stat != "Count")
                {
                    alert("You cannot calculate the " + stat + " statistic on the ungated population.");
                    return;
                }
            }
            statistic += stat;
        }
        appendLine(document.getElementsByName("statistics")[0], statistic);
    }

    function addGraph()
    {
        var subset = getValue(document.getElementById("graph_subset"));
        var x = getValue(document.getElementById("graph_x"));
        var y = getValue(document.getElementById("graph_y"));
        var graph = "";
        if (subset)
        {
            graph = subset;
        }
        if (y)
        {
            graph += "(" + x + ":" + y + ")";
        }
        else
        {
            graph += "(" + x + ")";
        }
        appendLine(document.getElementsByName("graphs")[0], graph);
    }
    
    function getValue(el)
    {
        if (el.options)
            return el.options[el.selectedIndex].value;
        return el.value;
    }

    function appendLine(el, text)
    {
        value = el.value;
        if (value && value.charAt(value.length - 1) != '\n')
        {
            value = value + '\n';
        }
        value += text;
        el.value = value;
    }
</script>

<form method="post" action="<%=formAction(ScriptController.Action.editAnalysis)%>">
    <p class="normal">
        <b>Statistics</b><br>
        Which statistics do you want to calculate? Enter one statistic per line.<br>
        <textarea name="statistics" rows="10" cols="60" wrap="off"><%=h(bean.statistics)%></textarea><br>
        <table>
            <tr><th>Subset</th><th>Statistic</th><th>Parameter</th><th>Percentile</th></tr>
            <tr><td>
                <select id="stat_subset">
                    <option value="*">[[All]]</option>
                    <option value="">Ungated</option>
                    <% for (SubsetSpec subset : subsets)
                    { %>
                    <option value="<%=h(subset)%>"><%=h(subset)%></option>
                    <% } %>
                </select></td>
                <td>
                    <select id="stat_stat">
                        <option value="Count">Count</option>
                        <option value="Frequency">Frequency</option>
                        <option value="Freq_Of_Parent">Freq_Of_Parent</option>
                        <option value="Freq_Of_Grandparent">Freq_Of_Grandparent</option>
                        <option value="Median">Median</option>
                        <option value="Mean">Mean</option>
                        <option value="Std_Dev">Standard Deviation</option>
                        <option value="CV">Coefficient of Variance (CV)</option>
                        <option value="Percentile">Percentile</option>
                    </select>
                </td>
                <td>
                    <select id="stat_parameter">
                        <option value="*">[[All]]</option>
                        <% for(Map.Entry<String,String> param : params.entrySet()) { %>
                            <option value="<%=h(param.getKey())%>"><%=h(param.getValue())%></option>
                        <% } %>
                    </select>
                </td>
                <td>
                    <input id="stat_parameter2">
                </td>
                <td><input type="button" onclick="addStat()" value="Add Statistic"></td>
            </tr>
        </table>

    </p>
    <p class="normal">
        <b>Graphs</b><br>
        Which graphs do you want to have drawn? Enter one graph per line.<br>
        <textarea name="graphs" rows="10" cols="60" wrap="off"><%=h(bean.graphs)%></textarea><br>
        <table>
            <tr><th>Subset</th><th>X Axis</th><th>Y Axis</th></tr>
            <tr><td>
                <select id="graph_subset">
                    <option value="">Ungated</option>
                    <% for (SubsetSpec subset : subsets)
                    { %>
                    <option value="<%=h(subset)%>"><%=h(subset)%></option>
                    <% } %>
                </select></td>
                <td>
                    <select id="graph_x">
                        <% for(Map.Entry<String,String> param : form.getParameters().entrySet()) {%>
                        <option value="<%=h(param.getKey())%>"><%=h(param.getValue())%></option>
                        <% } %>
                    </select>
                </td>
                <td>
                    <select id="graph_y">
                        <option value="">[[histogram]]</option>
                        <% for(Map.Entry<String,String> param : params.entrySet()) { %>
                            <option value="<%=h(param.getKey())%>"><%=h(param.getValue())%></option>
                        <% } %>
                    </select>
                </td>
                <td><input type="button" onclick="addGraph()" value="Add Graph"></td>
            </tr>
        </table>
    </p>
    <p class="normal">
        <b>Additional Subsets</b><br>
        Use this textbox to specify boolean expressions involving subsets that you want to calculate statistics for.
        A boolean subset expression has parentheses around it, and uses the operators '&' (and), '|' (or), and '!' (not).
        Example:<br>
        Lymph/CD4/CD8/(IFNg+&!IL2+)<br>
        <textarea rows="10" cols="60" wrap="off" name="subsets"><%=h(bean.subsets)%></textarea>
    </p>

    <input type="submit" value="Submit">

</form>