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

package org.labkey.ms2;

import org.labkey.api.data.BeanObjectFactory;
import org.labkey.api.reader.SimpleXMLStreamReader;

import javax.xml.stream.XMLStreamException;
import java.io.InputStream;
import java.util.Map;
import java.util.HashMap;

/**
 * User: arauch
 * Date: Jan 25, 2006
 * Time: 9:29:35 AM
 */
public class BeanXMLStreamReader extends SimpleXMLStreamReader
{
    public BeanXMLStreamReader(InputStream stream) throws XMLStreamException
    {
        super(stream);
    }


    // Creates & populates bean of the given class from XML attributes.
    // Create and register a BeanObjectFactory for this class first, unless the default factory will do.
    // In particular, you may need to override convertToPropertyName to convert attribute names.
    public <K> K beanFromAttributes(Class<K> clazz)
    {
        Map<String, Object> m = new HashMap<String, Object>();
        int attributeCount = getAttributeCount();
        BeanObjectFactory bof = (BeanObjectFactory) BeanObjectFactory.Registry.getFactory(clazz);

        for (int i = 0; i < attributeCount; i++)
        {
            String name = bof.convertToPropertyName(getAttributeLocalName(i));
            String value = getAttributeValue(i);
            m.put(name, value);
        }

        return (K)bof.fromMap(m);
    }
}
