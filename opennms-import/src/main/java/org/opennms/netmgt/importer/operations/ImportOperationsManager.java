//
// This file is part of the OpenNMS(R) Application.
//
// OpenNMS(R) is Copyright (C) 2006 The OpenNMS Group, Inc.  All rights reserved.
// OpenNMS(R) is a derivative work, containing both original code, included code and modified
// code that was published under the GNU General Public License. Copyrights for modified 
// and included code are below.
//
// OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
//
// Modifications:
//
// 2007 Jun 24: Use Java 5 generics. - dj@opennms.org
//
// Original code base Copyright (C) 1999-2001 Oculan Corp.  All rights reserved.
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.                                                            
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
//    
// For more information contact: 
//   OpenNMS Licensing       <license@opennms.org>
//   http://www.opennms.org/
//   http://www.opennms.com/
//
// Tab Size = 8

package org.opennms.netmgt.importer.operations;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Category;
import org.opennms.core.utils.ThreadCategory;
import org.opennms.netmgt.dao.OnmsDao;
import org.opennms.netmgt.eventd.EventIpcManager;
import org.opennms.netmgt.xml.event.Event;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import EDU.oswego.cs.dl.util.concurrent.LinkedQueue;
import EDU.oswego.cs.dl.util.concurrent.PooledExecutor;

/**
 * This nodes job is to tracks nodes that need to be deleted, added, or changed
 * @author david
 *
 */
public class ImportOperationsManager {
    
	private List<ImportOperation> m_inserts = new LinkedList<ImportOperation>();
    private List<ImportOperation> m_updates = new LinkedList<ImportOperation>();
    private Map<String, Integer> m_foreignIdToNodeMap;
    
    private ImportOperationFactory m_operationFactory;
    private ImportStatistics m_stats = new DefaultImportStatistics();
	private EventIpcManager m_eventMgr;
	
	private int m_scanThreads = 50;
	private int m_writeThreads = 4;
    private String m_foreignSource;
    
    public ImportOperationsManager(Map<String, Integer> foreignIdToNodeMap, ImportOperationFactory operationFactory) {
        m_foreignIdToNodeMap = new HashMap<String, Integer>(foreignIdToNodeMap);
        m_operationFactory = operationFactory;
    }

    public SaveOrUpdateOperation foundNode(String foreignId, String nodeLabel, String building, String city) {
        
        if (nodeExists(foreignId)) {
            return updateNode(foreignId, nodeLabel, building, city);
        } else {
            return insertNode(foreignId, nodeLabel, building, city);
        }        
    }

    private boolean nodeExists(String foreignId) {
        return m_foreignIdToNodeMap.containsKey(foreignId);
    }
    
    private SaveOrUpdateOperation insertNode(String foreignId, String nodeLabel, String building, String city) {
        InsertOperation insertOperation = m_operationFactory.createInsertOperation(getForeignSource(), foreignId, nodeLabel, building, city);
        m_inserts.add(insertOperation);
        return insertOperation;
    }

    private SaveOrUpdateOperation updateNode(String foreignId, String nodeLabel, String building, String city) {
        Integer nodeId = processForeignId(foreignId);
        UpdateOperation updateOperation = m_operationFactory.createUpdateOperation(nodeId, getForeignSource(), foreignId, nodeLabel, building, city);
        m_updates.add(updateOperation);
        return updateOperation;
    }

    /**
     * Return NodeId and remove it from the Map so we know which nodes have been operated on thereby
     * tracking nodes to be deleted.
     * @param foreignId
     * @return a nodeId
     */
    private Integer processForeignId(String foreignId) {
        return (Integer)m_foreignIdToNodeMap.remove(foreignId);
    }
    
    public int getOperationCount() {
        return m_inserts.size() + m_updates.size() + m_foreignIdToNodeMap.size();
    }
    
    public int getInsertCount() {
    	return m_inserts.size();
    }

    public int  getUpdateCount() {
        return m_updates.size();
    }

    public int getDeleteCount() {
    	return m_foreignIdToNodeMap.size();
    }
    
    class DeleteIterator implements Iterator {
    	
    	private Iterator m_foreignIdIterator = m_foreignIdToNodeMap.entrySet().iterator();

		public boolean hasNext() {
			return m_foreignIdIterator.hasNext();
		}

		public Object next() {
            Map.Entry entry = (Map.Entry)m_foreignIdIterator.next();
            Integer nodeId = (Integer)entry.getValue();
            String foreignId = (String)entry.getKey();
            return m_operationFactory.createDeleteOperation(nodeId, m_foreignSource, foreignId);
			
		}

		public void remove() {
			m_foreignIdIterator.remove();
		}
    	
    }
    
    class OperationIterator implements Iterator {
    	
    	Iterator<Iterator> m_iterIter;
    	Iterator m_currentIter;
    	
    	OperationIterator() {
    		List<Iterator> iters = new ArrayList<Iterator>(3);
    		iters.add(new DeleteIterator());
    		iters.add(m_updates.iterator());
    		iters.add(m_inserts.iterator());
    		m_iterIter = iters.iterator();
    	}
    	
		public boolean hasNext() {
			while((m_currentIter == null || !m_currentIter.hasNext()) && m_iterIter.hasNext()) {
				m_currentIter = m_iterIter.next();
				m_iterIter.remove();
			}
			
			return (m_currentIter == null ? false: m_currentIter.hasNext());
		}

		public Object next() {
			return m_currentIter.next();
		}

		public void remove() {
			m_currentIter.remove();
		}
    	
    	
    }

    public void persistOperations(TransactionTemplate template, OnmsDao dao) {
    	m_stats.beginProcessingOps();
    	m_stats.setDeleteCount(getDeleteCount());
    	m_stats.setInsertCount(getInsertCount());
    	m_stats.setUpdateCount(getUpdateCount());
		PooledExecutor dbPool = new PooledExecutor(new LinkedQueue());
		dbPool.setMinimumPoolSize(m_writeThreads);

		preprocessOperations(template, dao, new OperationIterator(), dbPool);
    	
		dbPool.shutdownAfterProcessingCurrentlyQueuedTasks();
		try { dbPool.awaitTerminationAfterShutdown(); } catch (InterruptedException e) { log().error("persister interrupted!", e); }
		
		m_stats.finishProcessingOps();
    	
    }
    
	private void preprocessOperations(final TransactionTemplate template, final OnmsDao dao, OperationIterator iterator, final PooledExecutor dbPool) {
		
		m_stats.beginPreprocessingOps();
		
		PooledExecutor threadPool = new PooledExecutor(new LinkedQueue());
		threadPool.setMinimumPoolSize(m_scanThreads);
		for (Iterator it = iterator; it.hasNext();) {
    		final ImportOperation oper = (ImportOperation) it.next();
    		Runnable r = new Runnable() {
    			public void run() {
    				preprocessOperation(oper, template, dao, dbPool);
    			}
    		};
    		try { threadPool.execute(r); } catch (InterruptedException e) { System.err.println("Interrupted!"); e.printStackTrace(); }
    		it.remove();
    	}
		threadPool.shutdownAfterProcessingCurrentlyQueuedTasks();
		try { threadPool.awaitTerminationAfterShutdown(); } catch (InterruptedException e) {log().error("preprocessor interrupted!", e);}
		
		m_stats.finishPreprocessingOps();
	}

	protected void preprocessOperation(final ImportOperation oper, final TransactionTemplate template, final OnmsDao dao, final PooledExecutor dbPool) {
		m_stats.beginPreprocessing(oper);
		log().info("Preprocess: "+oper);
		oper.gatherAdditionalData();
		Runnable r = new Runnable() {
			public void run() {
				persistOperation(oper, template, dao);
			}
		};
		try { dbPool.execute(r); } catch (InterruptedException e) { }
		m_stats.finishPreprocessing(oper);
	}

	protected void persistOperation(final ImportOperation oper, TransactionTemplate template, final OnmsDao dao) {
		m_stats.beginPersisting(oper);
		log().info("Persist: "+oper);

		List<Event> events = persistToDatabase(oper, template);
		
		m_stats.finishPersisting(oper);
		
		
		if (m_eventMgr != null && events != null) {
			m_stats.beginSendingEvents(oper, events);
			log().info("Send Events: "+oper);
			// now send the events for the update
			for (Iterator<Event> eventIt = events.iterator(); eventIt.hasNext();) {
				Event event = eventIt.next();
				m_eventMgr.sendNow(event);
			}
			m_stats.finishSendingEvents(oper, events);
		}

		log().info("Clear cache: "+oper);
		// clear the cache to we don't use up all the memory
		dao.clear();
	}

	/**
     * Persist the import operation changes to the database.
     *  
     * @param oper changes to persist
     * @param template transaction template in which to perform the persist operation
     * @return list of events
	 */
    @SuppressWarnings("unchecked")
    private List<Event> persistToDatabase(final ImportOperation oper, TransactionTemplate template) {
		List<Event> events = (List<Event>) template.execute(new TransactionCallback() {
			public Object doInTransaction(TransactionStatus status) {
				List<Event> result = oper.persist();
                return result;
			}
		});
        return events;
    }

	private Category log() {
		return ThreadCategory.getInstance(getClass());
	}

	public void setScanThreads(int scanThreads) {
		m_scanThreads = scanThreads;
	}
	
	public void setWriteThreads(int writeThreads) {
		m_writeThreads = writeThreads;
	}



	public EventIpcManager getEventMgr() {
		return m_eventMgr;
	}



	public void setEventMgr(EventIpcManager eventMgr) {
		m_eventMgr = eventMgr;
	}

	public ImportStatistics getStats() {
		return m_stats;
	}

	public void setStats(ImportStatistics stats) {
		m_stats = stats;
	}

    public void setForeignSource(String foreignSource) {
        m_foreignSource = foreignSource;
    }

    public String getForeignSource() {
        return m_foreignSource;
    }
}
