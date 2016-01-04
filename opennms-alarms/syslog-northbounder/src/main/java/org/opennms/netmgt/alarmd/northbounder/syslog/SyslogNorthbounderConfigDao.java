/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2012-2014 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2014 The OpenNMS Group, Inc.
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

package org.opennms.netmgt.alarmd.northbounder.syslog;

import org.opennms.core.xml.AbstractJaxbConfigDao;

/**
 * The Class SyslogNorthbounderConfigDao.
 * 
 * @author <a href="mailto:david@opennms.org>David Hustace</a>
 */
public class SyslogNorthbounderConfigDao extends AbstractJaxbConfigDao<SyslogNorthbounderConfig, SyslogNorthbounderConfig> {

    /**
     * Instantiates a new Syslog northbounder configiguration DAO.
     */
    public SyslogNorthbounderConfigDao() {
        super(SyslogNorthbounderConfig.class, "Config for Syslog Northbounder");
    }

    /* (non-Javadoc)
     * @see org.opennms.core.xml.AbstractJaxbConfigDao#translateConfig(java.lang.Object)
     */
    @Override
    protected SyslogNorthbounderConfig translateConfig( SyslogNorthbounderConfig config) {
        return config;
    }

    /**
     * Gets the Syslog northbounder configuration.
     *
     * @return the configuration object
     */
    public SyslogNorthbounderConfig getConfig() {
        return getContainer().getObject();
    }

    /**
     * Gets the Syslog destination.
     *
     * @param syslogDestinationName the Syslog destination name
     * @return the Syslog destination
     */
    public SyslogDestination getSyslogDestination(String syslogDestinationName) {
        for (SyslogDestination dest : getConfig().getDestinations()) {
            if (dest.getName().equals(syslogDestinationName)) {
                return dest;
            }
        }
        return null;
    }

    /**
     * Reload.
     */
    public void reload() {
        getContainer().reload();
    }
}