package Flow;

import org.fhcrc.cpas.view.ViewController;
import org.fhcrc.cpas.view.ViewURLHelper;
import org.fhcrc.cpas.flow.util.PFUtil;

abstract public class BaseController<A extends Enum, P extends Enum> extends ViewController
{
    public ViewURLHelper urlFor(Enum action)
    {
        return PFUtil.urlFor(action, getContainerPath());
    }

    protected int getIntParam(P param)
    {
        String value = getParam(param);
        if (value == null)
            return 0;
        return Integer.valueOf(value);
    }

    protected String getParam(P param)
    {
        return getRequest().getParameter(param.toString());
    }

    protected void putParam(ViewURLHelper helper, Enum param, String value)
    {
        helper.replaceParameter(param.toString(), value);
    }

    protected void putParam(ViewURLHelper helper, Enum param, int value)
    {
        putParam(helper, param, Integer.toString(value));
    }

    protected boolean hasParameter(String name)
    {
        if (getRequest().getParameter(name) != null)
            return true;
        if (getRequest().getParameter(name + ".x") != null)
            return true;
        return false;
    }

    public String getContainerPath()
    {
        return getViewURLHelper().getExtraPath();
    }
}
