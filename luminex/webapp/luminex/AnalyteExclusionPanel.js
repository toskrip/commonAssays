/*
 * Copyright (c) 2011-2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext.namespace('LABKEY');

// function called onclick of 'Exclude Analytes' button to open the run exclusion window 
function analyteExclusionWindow(assayName, runId)
{
    var win = new Ext.Window({
        cls: 'extContainer',
        title: 'Exclude Analytes from Analysis',
        layout:'fit',
        width: Ext.getBody().getViewSize().width < 490 ? Ext.getBody().getViewSize().width * .9 : 440,
        height: Ext.getBody().getViewSize().height > 500 ? 460 : Ext.getBody().getViewSize().height * .75,
        padding: 15,
        modal: true,
        closeAction:'close',
        bodyStyle: 'background-color: white;',
        items: new LABKEY.AnalyteExclusionPanel({
            assayName: assayName,
            runId: runId,
            listeners: {
                scope: this,
                'closeWindow': function(){
                    win.close();
                }
            }
        })
    });
    win.show(this);
}

/**
 * Class to display panel for selecting which analytes for a given replicate group to exlude from a Luminex run
 * @params assayName = the assay design name
 * @params runId = runId for the selected replicate group
 */
LABKEY.AnalyteExclusionPanel = Ext.extend(LABKEY.BaseExclusionPanel, {

    initComponent : function() {
        // query the RunExclusion table to see if there are any existing exclusions for this run
        this.queryExistingExclusions('RunExclusion', [LABKEY.Filter.create('runId', this.runId)], 'RunId,Comment,Analytes/RowId');

        LABKEY.AnalyteExclusionPanel.superclass.initComponent.call(this);
    },

    setupWindowPanelItems: function()
    {
        this.addHeaderPanel('Analytes excluded for a replicate group or titration will not be re-included by changes in assay level exclusions');

        var selMod = this.getGridCheckboxSelectionModel();

        // set the title for the grid panel based on previous exclusions
        var title = "Select the checkbox next to the analytes to be excluded";
        if (this.exclusionsExist)
        {
            title += "<BR/><span style='color:red;font-style:italic;'>Uncheck analytes to remove exclusions</span>";
        }

        // grid of avaialble/excluded analytes
        var availableAnalytesGrid = new Ext.grid.GridPanel({
            id: 'availableanalytes',
            style: 'padding-top: 10px;',
            title: title,
            headerStyle: 'font-weight: normal; background-color: #ffffff',            
            store:  new LABKEY.ext.Store({
                sql: "SELECT DISTINCT x.Analyte.RowId AS RowId, x.Analyte.Name AS Name "
                    + " FROM Data AS x WHERE x.Data.Run.RowId = " + this.runId,
                schemaName: 'assay.Luminex.' + LABKEY.QueryKey.encodePart(this.assayName),
                autoLoad: true,
                listeners: {
                    scope: this,
                    'load': function(store, records, options){
                        if (this.analytes)
                        {
                            // preselect any previously excluded analytes
                            availableAnalytesGrid.getSelectionModel().suspendEvents(false);
                            Ext.each(this.analytes, function(analyte){
                                var index = store.find('RowId', analyte);
                                availableAnalytesGrid.getSelectionModel().selectRow(index, true);
                            });
                            availableAnalytesGrid.getSelectionModel().resumeEvents();
                        }
                    }
                },
                sortInfo: {
                    field: 'Name',
                    direction: 'ASC'
                }
            }),
            colModel: new Ext.grid.ColumnModel({
                columns: [
                    selMod,
                    {header: 'Available Analytes', sortable: false, dataIndex: 'Name', menuDisabled: true}
                ]
            }),
            autoExpandColumn: 'Name',
            viewConfig: {
                forceFit: true
            },
            sm: selMod,
            anchor: '100%',
            height: 165,
            frame: false,
            loadMask: true
        });
        this.add(availableAnalytesGrid);

        this.addCommentPanel();

        this.addStandardButtons();

        this.doLayout();

        this.queryForRunAssayId();
    },

    insertUpdateExclusions: function(){

        this.mask("Saving analyte exclusions...");

        // generage a comma delim string of the analyte Ids to exclude
        var analytesForExclusion = this.findById('availableanalytes').getSelectionModel().getSelections();
        var analyteRowIds = "";
        var analyteNames = "";
        var sep = "";
        Ext.each(analytesForExclusion, function(record){
            analyteRowIds += sep.trim() + record.data.RowId;
            analyteNames += sep + record.data.Name;
            sep = ", ";
        });

        // config of data to save for the given analyte exclusion
        var config = {
            assayName: this.assayName,
            tableName: 'RunExclusion',
            runId: this.runId,
            commands: [{
                key: this.runId,
                analyteRowIds: (analyteRowIds != "" ? analyteRowIds : null),
                analyteNames: (analyteNames != "" ? analyteNames : null), // for logging purposes only
                comment: this.findById('comment').getValue()
            }]
        };

        // if we don't have an exclusion to delete or anything to insert/update, do nothing
        if (!this.exclusionsExist && config.commands[0].analyteRowIds == null)
        {
            this.unmask();
            return;
        }

        if (config.commands[0].analyteRowIds == null)
        {
            // ask the user if they are sure they want to remove the exclusions before deleting
            config.commands[0].command = 'delete';
            this.confirmExclusionDeletion(config, 'Are you sure you want to remove all analyte exlusions for run Id ' + this.runId + '?', 'analyte');
        }
        else
        {
            config.commands[0].command = this.exclusionsExist ? 'update' : 'insert';
            this.saveExclusions(config, 'analyte');
        }
    }
});