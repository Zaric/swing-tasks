/*
 * Copyright 2011 Eike Kettner
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.eknet.swing.task.impl;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

import javax.swing.SwingWorker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.eknet.swing.task.Task;
import org.eknet.swing.task.TaskContext;

/**
 * @author <a href="mailto:eike.kettner@gmail.com">Eike Kettner</a>
 * @since 20.07.11 21:23
 */
public class TaskWorker<V, C> extends SwingWorker<V, C> implements PropertyChangeListener {
  private final static Logger log = LoggerFactory.getLogger(TaskWorker.class);
  
  private final Task<V, C> task;

  private String phase;
  private Long startedTimestamp = null;
  private Long finishedTimestamp = null;

  private boolean error = false;

  private TaskContext context;

  public TaskWorker(/*@NotNull*/ Task<V, C> task) {
    Util.checkNotNullArgument(task);
    this.task = task;
    //to have a startedTimestamp set when the first TaskListener
    //gets the started-change, we add this as the first property
    //listener that sets the startedTimestamp property.
    this.addPropertyChangeListener(this);
  }

  @Override
  protected V doInBackground() throws Exception {
    TrackerImpl<C> tracker = new TrackerImpl<C>(this);
    return task.execute(tracker);
  }

  @Override
  protected void process(List<C> chunks) {
    task.process(chunks);
  }

  public void setContext(TaskContext context) {
    this.context = context;
  }

  @Override
  protected void done() {
    this.finishedTimestamp = System.currentTimeMillis();
    try {
      task.done(get());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.error("Interrupted during get()", e);
      task.failed(e);
    } catch (CancellationException e) {
      log.debug("Task '{}/{}' cancelled by user", task.getId(), getContextId());
      task.failed(e);
    } catch (Exception e) {
      this.error = true;
      log.debug("Error executing task " + task.getId() + "/" + getContextId(), e);
      if (e instanceof ExecutionException) {
        task.failed(e.getCause());
      } else {
        task.failed(e);
      }
    }
  }

  private String getContextId() {
    if (context != null) {
      return context.getContextId();
    }
    return null;
  }

  public Long getStartedTimestamp() {
    return startedTimestamp;
  }

  public Long getFinishTimestamp() {
    return finishedTimestamp;
  }

  public String getPhase() {
    return phase;
  }

  public boolean isError() {
    return error;
  }

  public Task<V, C> getTask() {
    return task;
  }

  // ~~ PropertyChangeListener

  @Override
  public void propertyChange(PropertyChangeEvent evt) {
    if (evt.getPropertyName().equals("state")) {
      if (evt.getOldValue() instanceof StateValue && evt.getNewValue() instanceof StateValue) {
        StateValue o = (StateValue) evt.getOldValue();
        StateValue n = (StateValue) evt.getNewValue();
        if (o == StateValue.PENDING && n == StateValue.STARTED) {
          this.startedTimestamp = System.currentTimeMillis();
        }
      }
    }
  }


  // ~~ delegates from Tracker
  
  void publishProgress(int progress) {
    super.setProgress(progress);
  }

  void publishChunks(C... chunks) {
    super.publish(chunks);
  }

  public void setPhase(String phase) {
    final String oldphase = this.phase;
    this.phase = phase;
    firePropertyChange("phase", oldphase, phase);
  }
}
