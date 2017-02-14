/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2017-2017 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2017 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.netmgt.ticketer.jira.fieldmapper;

import java.util.Arrays;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 *  Wrapper class to help splitting string values, separated by "," and map each split value to its
 *  according (custom) jira field value.
 *
 *  E.g. "label1,label2" becomes ["label1", "label2"], or "label1,label" becomes [{"name":"label1"}, {"name":"label2"}]"
 */
public class ArrayWrapper {

    public Object map(Function<String, Object> itemFunction, String attributeValue) {
        return Arrays.stream(attributeValue.split(","))
                .map(eachItem -> itemFunction.apply(eachItem))
                .collect(Collectors.toList());
    }
}