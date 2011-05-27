<%
/*
 * Copyright (c) 2009-2010 LabKey Corporation
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
<%@ page import="java.util.Map" %>
<%@ page import="org.labkey.flow.analysis.web.StatisticSpec" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.flow.query.AttributeCache" %>
<%@ page import="org.labkey.flow.query.FlowPropertySet" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="org.labkey.flow.data.FlowProtocol" %>
<%@ page import="org.labkey.api.exp.api.ExpSampleSet" %>
<%@ page import="java.util.List" %>
<%@ page import="org.labkey.api.exp.property.DomainProperty" %>
<%
    ViewContext context = HttpView.currentContext();
    FlowPropertySet fps = new FlowPropertySet(context.getContainer());
    
    StringBuilder jsonStats = new StringBuilder();
    jsonStats.append("[");
    String comma = "";
    for (StatisticSpec spec : fps.getStatistics().keySet())
    {
        jsonStats.append(comma);
        jsonStats.append("{");
        jsonStats.append("text:").append(PageFlowUtil.jsString(spec.toString())).append(",");
        if (null == spec.getSubset())
            jsonStats.append("subset:'',");
        else
        {
            jsonStats.append("subset:").append(PageFlowUtil.jsString(spec.getSubset().toString())).append(",");
            if (null != spec.getSubset().getParent())
                jsonStats.append("parent:").append(PageFlowUtil.jsString(spec.getSubset().getParent().toString())).append(",");
        }
        jsonStats.append("stat:").append(PageFlowUtil.jsString(spec.getStatistic().getShortName())).append(",");
        jsonStats.append("param:").append(PageFlowUtil.jsString(spec.getParameter())).append(",");
        jsonStats.append("}");
        comma = ",\n";
    }
    jsonStats.append("]");

    List<String> sampleSetProperties = new ArrayList<String>();
    FlowProtocol protocol = FlowProtocol.ensureForContainer(context.getUser(), context.getContainer());
    if (protocol != null)
    {
        ExpSampleSet sampleSet = protocol.getSampleSet();
        if (sampleSet != null)
        {
            for (DomainProperty dp : sampleSet.getPropertiesForType())
                sampleSetProperties.add(dp.getName());
        }
    }

%>
<script type="text/javascript">
Ext.QuickTips.init();

function statisticsTree(statistics)
{
    var s, node, subset;
    var map = {};
    for (var i=0 ; i<statistics.length ; i++)
    {
        s = statistics[i];
        node = map[s.subset];
        if (!node)
        {
            var text = s.subset;
            if (s.parent && 0==text.indexOf(s.parent+"/"))
                text = text.substring(s.parent.length+1);
            if (0==text.indexOf("(") && text.length-1 == text.lastIndexOf(")"))
                text = text.substring(1,text.length-2);
            node = new Ext.tree.TreeNode(Ext.apply({},{text:text, qtipCfg:{text:s.subset}, expanded:true, uiProvider:Ext.tree.ColumnNodeUI, parentNode:null}, s));    // stash original object in data
            node.attributes.stats = [];
            map[s.subset] = node;
        }
        var name = s.stat;
        if (s.param)
            name = name + "(" + s.param + ")";
        node.attributes.stats.push(name);
    }
    for (subset in map)
    {
        node = map[subset];
        var parentSubset = node.attributes.parent;
        if (!parentSubset)
            parentSubset = '';
        var parent = map[parentSubset];
        if (parent && parent != node)
        {
            parent.appendChild(node);
            node.attributes.parentNode = parent;
        }
    }
    var treeData = [];
    for (subset in map)
    {
        node = map[subset];
        if (!node.attributes.parentNode && (node.childNodes.length > 0 /* || node.attributes.stats.length > 0 */))
            treeData.push(node);
    }
    return treeData;
}

var StatisticField = Ext.extend(Ext.form.TriggerField,
{
    onTriggerClick : function()
    {
        if (this.disabled)
        {
            return;
        }
        if (this.popup == null)
        {
            var tree = new Ext.tree.TreePanel({
                cls:'extContainer',
                rootVisible:false,
                useArrows:true,
                autoScroll:false,
                containerScroll:true,
//                width:800, height:400,
//                autoHeight:true,
                animate:true,
                enableDD:false
            });
            var root = new Ext.tree.TreeNode({text:'-', expanded:true});
            for (var i=0 ; i<FlowPropertySet.statsTreeData.length ; i++)
                root.appendChild(FlowPropertySet.statsTreeData[i]);
            tree.setRootNode(root);
            var sm = tree.getSelectionModel();
            sm.on("selectionchange", function(sm,curr,prev){
                var subset = curr.attributes.subset;
                var stats = curr.attributes.stats;
                if (!stats || !stats.length) return;
                var items = [];
                for (var i=0 ; i<stats.length ; i++)
                    items.push({text:stats[i]});
                var statMenu = new Ext.menu.Menu({
                    width:240,
                    cls:'extContainer',
                    items: items
                });
                statMenu.on("itemclick",function(mi,e){
                    var stat = mi.text=="%P"?"Freq_Of_Parent":mi.text;
                    statMenu.destroy();
                    this.pickValue(subset + ":" + stat);
                }, this);
                statMenu.show(curr.getUI().getTextEl());    
                //this.pickValue(curr.attributes.subset);
            }, this);

            this.popup = new Ext.Window({
                autoScroll:true,
                closeAction:'hide',
                closable:true,
                constrain:true,
                items:[tree],
                title:'Statistic Picker',
                width:800, height:400
            });
        }
        this.popup.show();
        this.popup.center();
    },

    pickValue : function(value)
    {
        if (this.popup) this.popup.hide();
        this.setValue(value);
    }

});



var FlowPropertySet = {};
FlowPropertySet.keywords = [<%
    comma = "";
    for (String s : fps.getVisibleKeywords())
    {
        %><%=comma%><%=PageFlowUtil.jsString(s)%><%
        comma=",";
    }
%>];
FlowPropertySet.statistics = <%=jsonStats%>;
FlowPropertySet.statsTreeData = statisticsTree(FlowPropertySet.statistics);

var SampleSet = {};
SampleSet.properties = [<%
    comma = "";
    for (String s : sampleSetProperties)
    {
        %><%=comma%><%=PageFlowUtil.jsString(s)%><%
        comma=",";
    }
%>];

Ext.reg('statisticField', StatisticField);

</script>
