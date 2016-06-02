/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2008-2014 The OpenNMS Group, Inc.
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

package org.opennms.core.tasks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/*
 * ContainerTask
 * @author brozow
 * 
 * TODO derive directly from Task
 */
/**
 * <p>Abstract ContainerTask class.</p>
 *
 * @author ranger
 * @version $Id: $
 */
public abstract class ContainerTask<T extends ContainerTask<?>> extends AbstractTask {

    /**
     * TaskTrigger
     *
     * @author brozow
     */
    private static final class TriggerTask extends AbstractTask {

        /**
         * TODO: Get rid of this backreference, it is only used in {@link #toString()}
         */
        private final ContainerTask<?> m_parent;

        public TriggerTask(TaskCoordinator coordinator, ContainerTask<?> parent) {
            super(coordinator, parent);
            m_parent = parent;
        }


        @Override
        protected void completeSubmit() {
            getCoordinator().markTaskAsCompleted(this);
        }


        @Override
        public String toString() { return "Trigger For " + m_parent.toString(); }
    }

    protected final AbstractTask m_triggerTask;
    private final List<Task> m_children = Collections.synchronizedList(new ArrayList<Task>());
    private final TaskBuilder<T> m_builder;
    
    /**
     * <p>Constructor for ContainerTask.</p>
     *
     * @param coordinator a {@link org.opennms.core.tasks.TaskCoordinator} object.
     * @param parent a {@link org.opennms.core.tasks.ContainerTask} object.
     */
    public ContainerTask(TaskCoordinator coordinator, ContainerTask<?> parent) {
        super(coordinator, parent);
        m_builder = createBuilder();
        m_triggerTask = new TriggerTask(coordinator, this);

    }



    @SuppressWarnings("unchecked")
    private TaskBuilder<T> createBuilder() {
        return new TaskBuilder<T>((T)this);
    }
    
    
    
    /**
     * <p>getBuilder</p>
     *
     * @return a {@link org.opennms.core.tasks.TaskBuilder} object.
     */
    public TaskBuilder<T> getBuilder() {
        return m_builder;
    }
    
    /** {@inheritDoc} */
    @Override
    public void addPrerequisite(AbstractTask task) {
        super.addPrerequisite(task);
        m_triggerTask.addPrerequisite(task);
    }

    /** {@inheritDoc} */
    @Override
    public void preSchedule() {
        m_triggerTask.schedule();
        List<Task> children;
        synchronized(m_children) {
            children = new ArrayList<Task>(m_children);
            m_children.clear();
        }
        
        for(Task task : children) {
            task.schedule();
        }
    }
    
    /**
     * <p>add</p>
     *
     * @param task a {@link org.opennms.core.tasks.Task} object.
     */
    public void add(AbstractTask task) {

        super.addPrerequisite(task);
        addChildDependencies(task);

        boolean scheduleChild;
        synchronized(m_children) {
            scheduleChild = isScheduled();
            if (!scheduleChild) {
                m_children.add(task);
            }
        }

        if (scheduleChild) {
            task.schedule();
        }
        
    }
    
    /**
     * <p>add</p>
     *
     * @param runInBatch a {@link org.opennms.core.tasks.RunInBatch} object.
     */
    public void add(RunInBatch runInBatch) {
        getBuilder().add(runInBatch);
    }
    
    /**
     * <p>add</p>
     *
     * @param needsContainer a {@link org.opennms.core.tasks.NeedsContainer} object.
     */
    public void add(NeedsContainer needsContainer) {
        getBuilder().add(needsContainer);
    }

    /**
     * <p>getTriggerTask</p>
     *
     * @return a {@link org.opennms.core.tasks.Task} object.
     */
    protected AbstractTask getTriggerTask() {
        return m_triggerTask;
    }

//    private void setPreferredExecutorOfChild(Task task) {
//        if (task instanceof ContainerTask) {
//            ContainerTask container = (ContainerTask)task;
//            if (container.getChildPreferredExecutor().equals(DEFAULT_EXECUTOR)) {
//                container.setPreferredExecutor(getChildPreferredExecutor());
//            }
//        } else if (task instanceof SyncTask){
//            SyncTask syncTask = (SyncTask)task;
//            if (syncTask.getPreferredExecutor().equals(DEFAULT_EXECUTOR)) {
//                syncTask.setPreferredExecutor(getChildPreferredExecutor());
//            }
//        }
//    }
    
    
    
    /** {@inheritDoc} */
    @Override
    protected void completeSubmit() {
        getCoordinator().markTaskAsCompleted(this);
    }

    /**
     * <p>add</p>
     *
     * @param runnable a {@link java.lang.Runnable} object.
     * @return a {@link org.opennms.core.tasks.SyncTask} object.
     */
    public SyncTask add(Runnable runnable) {
        SyncTask task = createTask(runnable);
        add(task);
        return task;
    }
    
    /**
     * <p>add</p>
     *
     * @param runnable a {@link java.lang.Runnable} object.
     * @param schedulingHint a {@link java.lang.String} object.
     * @return a {@link org.opennms.core.tasks.SyncTask} object.
     */
    public SyncTask add(Runnable runnable, String schedulingHint) {
        SyncTask task = createTask(runnable, schedulingHint);
        add(task);
        return task;
    }

    private SyncTask createTask(Runnable runnable) {
        return getCoordinator().createTask(this, runnable);
    }

    private SyncTask createTask(Runnable runnable, String schedulingHint) {
        return getCoordinator().createTask(this, runnable, schedulingHint);
    }

    /**
     * <p>addChildDependencies</p>
     *
     * @param child a {@link org.opennms.core.tasks.AbstractTask} object.
     */
    protected void addChildDependencies(AbstractTask child) {
        child.addPrerequisite(m_triggerTask);
    }
}
