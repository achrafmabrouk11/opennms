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

package org.opennms.features.topology.app.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.opennms.features.topology.api.BoundingBox;
import org.opennms.features.topology.api.Layout;
import org.opennms.features.topology.api.Point;
import org.opennms.features.topology.api.topo.Vertex;
import org.opennms.features.topology.api.topo.VertexRef;

import com.google.common.collect.Lists;

public class DefaultLayout implements Layout {
	
	private final Map<VertexRef, Point> m_locations = new HashMap<>();

	private Collection<Vertex> displayVertices = Lists.newArrayList();

	@Override
	public Point getLocation(VertexRef v) {
		if (v == null) {
			throw new IllegalArgumentException("Cannot fetch location of null vertex");
		}
		Point p = m_locations.get(v);
		if (p == null) {
			return new Point(0, 0);
		}
		return p;
	}

	public void setDisplayVertices(Collection<Vertex> displayVertices) {
		this.displayVertices = displayVertices;
	}

	@Override
	public Map<VertexRef,Point> getLocations() {
		return Collections.unmodifiableMap(new HashMap<>(m_locations));
	}

	@Override
	public void setLocation(VertexRef v, Point location) {
		if (v == null) {
			throw new IllegalArgumentException("Cannot set location of null vertex");
		}
		m_locations.put(v, location);
	}

	@Override
	public Point getInitialLocation(VertexRef v) {
		if (v == null) {
			throw new IllegalArgumentException("Cannot get initial location of null vertex");
		}
		return getLocation(v);
	}
	
    @Override
    public BoundingBox getBounds() {
        if(displayVertices.size() > 0) {
            Collection<VertexRef> vRefs = new ArrayList<>();
            for(Vertex v : displayVertices) {
                vRefs.add(v);
            }
            return computeBoundingBox(vRefs);
        } else {
            BoundingBox bBox = new BoundingBox();
            bBox.addPoint(new Point(0, 0));
            return bBox;
        }
    }
    
    private static BoundingBox computeBoundingBox(Layout layout, VertexRef vertRef) {
        return new BoundingBox(layout.getLocation(vertRef), 100, 100);
    }
    
    @Override
    public BoundingBox computeBoundingBox(Collection<VertexRef> vertRefs) {
        if(vertRefs != null && vertRefs.size() > 0) {
            BoundingBox boundingBox = new BoundingBox();
            for(VertexRef vertRef : vertRefs) {
                boundingBox.addBoundingbox( computeBoundingBox(this, vertRef) );
            }
            return boundingBox;
        } else {
            return getBounds();
        }
    }
}
