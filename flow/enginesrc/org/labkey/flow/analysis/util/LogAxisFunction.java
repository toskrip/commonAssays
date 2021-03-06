/*
 * Copyright (c) 2011-2019 LabKey Corporation
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

package org.labkey.flow.analysis.util;

import com.google.gwt.user.client.rpc.IsSerializable;

import java.io.Serializable;

/**
 * User: matthewb
 * Date: Sep 13, 2007
 * Time: 2:41:32 PM
 */
public class LogAxisFunction implements RangeFunction, IsSerializable, Serializable
{
    // can't use final keyword, it breaks GWT serialization
    private double logLinSwitch;
    // precomputed constants
    private double intersect;
    private double scale;
    private double adjust;

    public LogAxisFunction() // required for GWT serializability
    {
        this(-1);
    }

    public LogAxisFunction(double logLinSwitch)
    {
        this.logLinSwitch = logLinSwitch;
        // This scale makes sure that the linear slope is tangent to the log function
        intersect = 1/Math.log(10);                 // == log10(E) == .4343
        scale = intersect/logLinSwitch;             // magic slope to make 1st derivative continuous
        adjust = intersect - log10(logLinSwitch);
    }

    public double compute(double val)
    {
        if (logLinSwitch < 0) throw new IllegalStateException("logLinSwitch not set");
        double sign = signum(val);
        val = Math.abs(val);
        if (val < logLinSwitch)
            return sign * val * scale;      // (0:logLinSwitch)->(0:1/log(10))
        else
            return sign * (log10(val) + adjust);   // (logLinSwitch:x)->(log(10):...
    }

    public double invert(double val)
    {
        if (logLinSwitch < 0) throw new IllegalStateException("logLinSwitch not set");
        double sign = signum(val);
        val = Math.abs(val);
        if (val < intersect)
            return sign * val / scale;
        else
            return sign * Math.pow(10, val - adjust);
    }

    // UNDONE in gwt
    private static final double _log10factor = 1.0/Math.log(10);
    private double log10(double x)
    {
        return Math.log(x) * _log10factor;
    }
    private double signum(double x)
    {
        return x < 0 ? -1  : 1; // zero is a don't care
    }
}
