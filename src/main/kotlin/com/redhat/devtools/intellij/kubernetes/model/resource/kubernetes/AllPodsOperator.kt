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
import com.redhat.devtools.intellij.kubernetes.model.resource.NonNamespacedOperation
import com.redhat.devtools.intellij.kubernetes.model.resource.NonNamespacedResourceOperator
import com.redhat.devtools.intellij.kubernetes.model.resource.ResourceKind
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.dsl.LogWatch
import java.io.OutputStream

class AllPodsOperator(clients: Clients<out KubernetesClient>)
    : NonNamespacedResourceOperator<Pod, KubernetesClient>(clients.get()), ILogWatcher<Pod> {

    companion object {
        val KIND = ResourceKind.create(Pod::class.java)
    }

    override val kind = KIND

    override fun getOperation(): NonNamespacedOperation<Pod> {
        return client.pods()
    }

    override fun get(resource: HasMetadata): HasMetadata? {
        return ensureSameNamespace(resource, super.get(resource))
    }

    override fun watchLog(resource: Pod, out: OutputStream): LogWatch? {
        return watchLogWhenReady(resource, out)
    }
}
