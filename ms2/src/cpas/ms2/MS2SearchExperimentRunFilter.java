package cpas.ms2;

import org.fhcrc.cpas.exp.ExperimentRunFilter;
import org.fhcrc.cpas.ms2.query.MS2Schema;
import org.fhcrc.cpas.data.ActionButton;
import org.fhcrc.cpas.data.DataRegion;
import org.fhcrc.cpas.data.ButtonBar;
import org.fhcrc.cpas.data.ButtonBarLineBreak;
import org.fhcrc.cpas.view.ViewContext;
import org.fhcrc.cpas.view.ViewURLHelper;
import org.fhcrc.cpas.security.ACL;

/**
 * User: jeckels
 * Date: Nov 7, 2006
 */
public class MS2SearchExperimentRunFilter extends ExperimentRunFilter
{
    public MS2SearchExperimentRunFilter(String name, String tableName)
    {
        super(name, MS2Schema.SCHEMA_NAME, tableName);
    }

    private ActionButton createButton(ViewContext context, String actionName, String description, ActionButton.Action method)
    {
        ViewURLHelper url = context.getViewURLHelper().clone();
        url.deleteParameters();
        url.setPageFlow("MS2");
        url.setAction(actionName + ".view");
        ActionButton button = new ActionButton(url.getLocalURIString() + "ExperimentRunIds=true", description, ACL.PERM_READ, method);
        button.setDisplayModes(DataRegion.MODE_GRID);
        return button;
    }

    public void populateButtonBar(ViewContext context, ButtonBar bar)
    {
        bar.add(new ButtonBarLineBreak());

        bar.add(createButton(context, "compareProteins", "Compare Proteins", ActionButton.Action.POST));
        bar.add(createButton(context, "compareProteinProphetProteins", "Compare Protein Prophet", ActionButton.Action.POST));
        bar.add(createButton(context, "comparePeptides", "Compare Peptides", ActionButton.Action.POST));

        ActionButton exportRuns = new ActionButton("button", "Export MS2 Data");
        ViewURLHelper url = context.getViewURLHelper().clone();
        url.deleteParameters();
        url.setPageFlow("MS2");
        url.setAction("pickExportRunsView.view");
        exportRuns.setScript("return verifySelected(this.form, \"" + url.getLocalURIString() + "ExperimentRunIds=true\", \"post\", \"runs\")");
        exportRuns.setActionType(ActionButton.Action.GET);
        exportRuns.setDisplayPermission(ACL.PERM_READ);
        bar.add(exportRuns);

        bar.add(createButton(context, "showHierarchy.view", "Show Hierarchy", ActionButton.Action.LINK));

        url.setAction("selectMoveLocation.view");
        ActionButton moveRuns = new ActionButton("", "Move Runs");
        moveRuns.setScript("return verifySelected(this.form, \"" + url.getLocalURIString() +  "ExperimentRunIds=true\", \"post\", \"runs\")");
        moveRuns.setActionType(ActionButton.Action.GET);
        moveRuns.setDisplayPermission(ACL.PERM_DELETE);
        bar.add(moveRuns);
    }
}
