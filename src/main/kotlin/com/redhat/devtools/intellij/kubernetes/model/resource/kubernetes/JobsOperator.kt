/*******************************************************************************
 * Copyright (c) 2020 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.intellij.kubernetes.model.resource.kubernetes

import com.redhat.devtools.intellij.kubernetes.model.Clients
import com.redhat.devtools.intellij.kubernetes.model.resource.ILogWatcher
import com.redhat.devtools.intellij.kubernetes.model.resource.NamespacedOperation
import com.redhat.devtools.intellij.kubernetes.model.resource.NamespacedResourceOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.ResourceKind
import io.fabric8.kubernetes.api.model.batch.v1.Job
import io.fabric8.kubernetes.client.BatchAPIGroupClient
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.dsl.LogWatch
import java.io.OutputStream

class JobsOperator(clients: Clients<out KubernetesClient>)
    : NamespacedResourceOperator<Job, BatchAPIGroupClient>(clients.getBatch()), ILogWatcher<Job> {

    companion object {
        val KIND = ResourceKind.create(Job::class.java)
    }

    override val kind = KIND

    override fun getOperation(): NamespacedOperation<Job> {
        return client.v1().jobs()
    }

    override fun watchLog(resource: Job, out: OutputStream): LogWatch? {
        return watchLogWhenReady(resource, out)
    }
}
