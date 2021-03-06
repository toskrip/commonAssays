/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

package org.labkey.ms2.pipeline.sequest;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.pipeline.PipelineJobException;

import java.util.List;

/**
 * User: billnelson@uky.edu
 * Date: Sep 8, 2006
 * Time: 12:21:56 PM
 */
public class SequestParamsException extends PipelineJobException
{
    public SequestParamsException(String message)
    {
        super(message);
    }

    public SequestParamsException(List<String> messages)
    {
        super(StringUtils.join(messages, '\n'));
    }

    public SequestParamsException(Exception e)
    {
        super(e);
    }

    public SequestParamsException(String message, Exception e)
    {
        super(message, e);
    }
}
