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

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.clearInvocations
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.redhat.devtools.intellij.kubernetes.model.Clients
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.NAMESPACE1
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.NAMESPACE2
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.NAMESPACE3
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.POD1
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.POD2
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.POD3
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.client
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.inNamespace
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.items
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.list
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.pods
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.resource
import com.redhat.devtools.intellij.kubernetes.model.mocks.ClientMocks.withName
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.PodBuilder
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.Watcher
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test

class NamespacedPodsOperatorTest {

    private val currentNamespace = NAMESPACE2.metadata.name
    private val clients = Clients(client(currentNamespace, arrayOf(NAMESPACE1, NAMESPACE2, NAMESPACE3)))
    private val operator = spy(TestablePodsOperator(clients))
    private val op = inNamespace(pods(clients.get()))

    @Before
    fun before() {
        items(list(op), POD1, POD2, POD3) // list
        withName(op, POD2) // create, replace, get
        operator.namespace = currentNamespace
    }

    @Test
    fun `#getAllResources() returns cached pods, won't load a 2nd time`() {
        // given
        val namespace = NAMESPACE1.metadata.name
        operator.namespace =  namespace
        operator.allResources
        // when
        operator.allResources
        // then
        verify(operator, times(1)).loadAllResources(namespace)
    }

    @Test
    fun `#getAllResources() wont return cached but load pods if #invalidate() is called`() {
        // given
        val namespace = NAMESPACE1.metadata.name
        operator.namespace =  namespace
        operator.allResources
        verify(operator, times(1)).loadAllResources(namespace)
        operator.invalidate()
        // when
        operator.allResources
        // then
        verify(operator, times(2)).loadAllResources(namespace)
    }

    @Test
    fun `#getAllResources() won't load resources if namespace is null`() {
        // given
        operator.namespace =  null
        clearInvocations(operator)
        // when
        operator.allResources
        // then
        verify(operator, never()).loadAllResources(any())
    }

    @Test
    fun `#setNamespace(namespace) sets namespace that's used in #loadAllResources(namespace)`() {
        // given
        val namespace = "darth vader"
        operator.namespace =  namespace
        val namespaceCaptor = argumentCaptor<String>()
        clearInvocations(operator)
        // when
        operator.allResources
        // then
        verify(operator).loadAllResources(namespaceCaptor.capture())
        assertThat(namespaceCaptor.firstValue).isEqualTo(namespace)
    }

    @Test
    fun `#setNamespace(namespace) invalidates cache`() {
        // given
        clearInvocations(operator)
        // when
        operator.namespace =  "skywalker"
        // then
        verify(operator).invalidate()
    }

    @Test
    fun `#replaced(pod) replaces pod if pod which is same resource already exist`() {
        // given
        val pod = resource<Pod>(POD2.metadata.name, POD2.metadata.namespace, POD2.metadata.uid, POD2.apiVersion)
        assertThat(operator.allResources).doesNotContain(pod)
        // when
        val replaced = operator.replaced(pod)
        // then
        assertThat(replaced).isTrue()
        assertThat(operator.allResources).contains(pod)
    }

    @Test
    fun `#replaced(pod) does NOT replace pod if pod has different name`() {
        // given
        val pod = resource<Pod>("darth vader", POD2.metadata.namespace, POD2.metadata.uid, POD2.apiVersion)
        assertThat(operator.allResources).doesNotContain(pod)
        // when
        val replaced = operator.replaced(pod)
        // then
        assertThat(replaced).isFalse()
        assertThat(operator.allResources).doesNotContain(pod)
    }

    @Test
    fun `#replaced(pod) does NOT replace pod if pod has different namespace`() {
        // given
        val pod = resource<Pod>(POD2.metadata.name, "sith", POD2.metadata.uid, POD2.apiVersion)
        assertThat(operator.allResources).doesNotContain(pod)
        // when
        val replaced = operator.replaced(pod)
        // then
        assertThat(replaced).isFalse()
        assertThat(operator.allResources).doesNotContain(pod)
    }

    @Test
    fun `#added(pod) adds pod if not contained yet`() {
        // given
        val pod = resource<Pod>("papa-smurf", "smurf forest", "smurfUid", "v1")
        assertThat(operator.allResources).doesNotContain(pod)
        // when
        operator.added(pod)
        // then
        assertThat(operator.allResources).contains(pod)
    }

    @Test
    fun `#added(pod) does not add if pod is already contained`() {
        // given
        val pod = operator.allResources.elementAt(0)
        // when
        val size = operator.allResources.size
        operator.added(pod)
        // then
        assertThat(operator.allResources).contains(pod)
        assertThat(operator.allResources.size).isEqualTo(size)
    }

    @Test
    fun `#added(pod) is replacing if different instance of same pod is already contained`() {
        // given
        val instance1 = resource<Pod>("gargamel", "smurfington", "uid-1-2-3", "v1")
        val instance2 = resource<Pod>("gargamel", "smurfington", "uid-1-2-3", "v1")
        operator.added(instance1)
        assertThat(operator.allResources).contains(instance1)
        // when
        operator.added(instance2)
        // then
        assertThat(operator.allResources).doesNotContain(instance1)
        assertThat(operator.allResources).contains(instance2)
    }

    @Test
    fun `#added(pod) returns true if pod was added`() {
        // given
        val pod = resource<Pod>("papa-smurf", "ns1", "papaUid", "v1")
        assertThat(operator.allResources).doesNotContain(pod)
        // when
        val added = operator.added(pod)
        // then
        assertThat(added).isTrue()
    }

    @Test
    fun `#added(pod) returns false if pod was not added`() {
        // given
        val pod = operator.allResources.elementAt(0)
        // when
        val added = operator.added(pod)
        // then
        assertThat(added).isFalse()
    }

    @Test
    fun `#removed(pod) removes the given pod`() {
        // given
        val pod = operator.allResources.elementAt(0)
        // when
        operator.removed(pod)
        // then
        assertThat(operator.allResources).doesNotContain(pod)
    }

    @Test
    fun `#removed(pod) removes the given pod if it isn't the same instance but is same pod`() {
        // given
        val pod1 = operator.allResources.elementAt(0)
        val pod2 = resource<Pod>(pod1.metadata.name, pod1.metadata.namespace, pod1.metadata.uid, pod1.apiVersion)
        // when
        operator.removed(pod2)
        // then
        assertThat(operator.allResources).doesNotContain(pod1)
    }

    @Test
    fun `#removed(pod) returns true if pod was removed`() {
        // given
        val pod = operator.allResources.elementAt(0)
        // when
        val removed = operator.removed(pod)
        // then
        assertThat(removed).isTrue()
    }

    @Test
    fun `#removed(pod) does not remove if pod is not contained`() {
        // given
        val pod = resource<Pod>("papa-smurf", "ns1", "papaUid", "v1")
        assertThat(operator.allResources).doesNotContain(pod)
        // when
        val size = operator.allResources.size
        operator.removed(pod)
        // then
        assertThat(operator.allResources).doesNotContain(pod)
        assertThat(operator.allResources.size).isEqualTo(size)
    }

    @Test
    fun `#removed(pod) returns false if pod was not removed`() {
        // given
        val pod = resource<Pod>("papa-smurf", "ns1", "papaUid", "v1")
        // when
        val removed = operator.removed(pod)
        // then
        assertThat(removed).isFalse()
    }

    @Test
    fun `#watchAll() watches all pods in given namespace using client`() {
        // given
        val watcher = mock<Watcher<Pod>>()
        // when
        operator.watchAll(watcher)
        // then
        verify(clients.get().pods()
            .inNamespace(POD2.metadata.namespace)
        ).watch(watcher)
    }

    @Test
    fun `#watchAll() does NOT watch if operator has no namespace`() {
        // given
        val watcher = mock<Watcher<Pod>>()
        operator.namespace = null
        // when
        operator.watchAll(watcher)
        // then
        verify(clients.get().pods()
            .inNamespace(POD2.metadata.namespace), never()
        ).watch(watcher)
    }

    @Test
    fun `#watch() watches given pod in client`() {
        // given
        val watcher = mock<Watcher<Pod>>()
        // when
        operator.watch(POD2, watcher)
        // then
        verify(clients.get().pods()
            .inNamespace(POD2.metadata.namespace)
            .withName(POD2.metadata.name)
        ).watch(watcher)
    }

    @Test
    fun `#delete() deletes given pods in client`() {
        // given
        // when
        val toDelete = listOf(POD2)
        operator.delete(toDelete)
        // then
        verify(clients.get().pods()).delete(toDelete)
    }

    @Test
    fun `#delete() won't delete if namespace is null`() {
        // given
        operator.namespace =  null
        clearInvocations(operator)
        // when
        operator.delete(listOf(POD2))
        // then
        verify(clients.get().pods(), never()).delete(any<List<Pod>>())
    }

    @Test
    fun `#delete() returns true if client could delete`() {
        // given
        clearInvocations(operator)
        whenever(clients.get().pods().delete(any<List<Pod>>()))
            .thenReturn(true)
        // when
        val success = operator.delete(listOf(POD2))
        // then
        assertThat(success).isTrue()
    }

    @Test
    fun `#delete() returns false if client could NOT delete`() {
        // given
        clearInvocations(operator)
        whenever(clients.get().pods().delete(any<List<Pod>>()))
            .thenReturn(false)
        // when
        val success = operator.delete(listOf(POD2))
        // then
        assertThat(success).isFalse()
    }

    @Test
    fun `#replace() is replacing given pod in client`() {
        // given
        // when
        operator.replace(POD2)
        // then
        verify(clients.get().pods()
            .inNamespace(POD2.metadata.namespace)
            .withName(POD2.metadata.name))
            .replace(POD2)
    }

    @Test
    fun `#replace() is removing and restoring resource version`() {
        // given
        val pod = resource<Pod>("pod2", "namespace2", "podUid2", "v1", "1")
        val resourceVersion = pod.metadata.resourceVersion
        // when
        operator.replace(pod)
        // then
        verify(pod.metadata).resourceVersion = null
        verify(pod.metadata).resourceVersion = resourceVersion
    }

    @Test
    fun `#replace() is removing uid`() {
        // given
        val pod = resource<Pod>("pod2", "namespace2", "podUid2", "v1", "1")
        clearInvocations(pod)
        val uid = pod.metadata.uid
        // when
        operator.replace(pod)
        // then
        verify(pod.metadata).uid = null
        verify(pod.metadata).uid = uid
    }

    @Test
    fun `#replace() will replace even if namespace is null`() {
        // given
        operator.namespace =  null
        clearInvocations(operator)
        // when
        operator.replace(POD2)
        // then
        verify(clients.get().pods()
            .inNamespace(POD2.metadata.namespace)
            .withName(POD2.metadata.name))
            .replace(POD2)
    }

    @Test
    fun `#replace() returns new pod if client replaced`() {
        // given
        clearInvocations(operator)
        whenever(clients.get().pods()
            .inNamespace(POD2.metadata.namespace)
            .withName(POD2.metadata.name)
            .replace(POD2))
            .thenReturn(POD3)
        // when
        val newPod = operator.replace(POD2)
        // then
        assertThat(newPod).isEqualTo(POD3)
    }

    @Test
    fun `#create() is creating given pod in client`() {
        // given
        // when
        operator.create(POD2)
        // then
        verify(clients.get().pods()
            .inNamespace(POD2.metadata.namespace)
            .withName(POD2.metadata.name))
            .create(POD2)
    }

    @Test
    fun `#create() is removing and restoring resource version`() {
        // given
        val pod = resource<Pod>("pod2", "namespace2", "podUid2", "v1", "1")

        clearInvocations(pod)
        val resourceVersion = pod.metadata.resourceVersion
        // when
        operator.create(pod)
        // then
        verify(pod.metadata).resourceVersion = null
        verify(pod.metadata).resourceVersion = resourceVersion
    }

    @Test
    fun `#create() is removing uid`() {
        // given
        val pod = resource<Pod>("pod2", "namespace2", "podUid2", "v1", "1")
        clearInvocations(pod)
        val uid = pod.metadata.uid
        // when
        operator.create(pod)
        // then
        verify(pod.metadata).uid = null
        verify(pod.metadata).uid = uid
    }

    @Test
    fun `#create() is creating even if namespace is null`() {
        // given
        operator.namespace =  null
        clearInvocations(operator)
        // when
        operator.create(POD2)
        // then
        verify(clients.get().pods()
            .inNamespace(POD2.metadata.namespace)
            .withName(POD2.metadata.name))
            .create(POD2)
    }

    @Test
    fun `#create() returns new pod if client created`() {
        // given
        clearInvocations(operator)
        whenever(clients.get().pods()
            .inNamespace(POD2.metadata.namespace)
            .withName(POD2.metadata.name)
            .create(POD2))
            .thenReturn(POD3)
        // when
        val newPod = operator.create(POD2)
        // then
        assertThat(newPod).isEqualTo(POD3)
    }

    @Test
    fun `#create() returns null if client could not create`() {
        // given
        clearInvocations(operator)
        whenever(clients.get().pods()
            .inNamespace(POD2.metadata.namespace)
            .withName(POD2.metadata.name)
            .create(POD2))
            .thenReturn(null)
        // when
        val newPod = operator.create(POD2)
        // then
        assertThat(newPod).isNull()
    }

    @Test
    fun `#get() is getting given pod in client`() {
        // given
        // when
        operator.get(POD2)
        // then
        verify(clients.get().pods()
            .inNamespace(POD2.metadata.namespace)
            .withName(POD2.metadata.name))
            .get()
    }

    @Test
    fun `#get() is using resource namespace if exists`() {
        // given
        val pod = PodBuilder(POD2).build()
        clearInvocations(operator)
        // when
        operator.get(pod)
        // then
        verify(clients.get().pods().inNamespace(operator.namespace))
            .withName(pod.metadata.name)
    }

    @Test
    fun `#get() is using operator namespace if resource namespace is null`() {
        // given
        val pod = PodBuilder(POD2).editMetadata()
            .withNamespace(null) // no namespace in pod
            .endMetadata()
            .build()
        operator.namespace =  "smurfington" // should use it
        clearInvocations(operator)
        // when
        operator.get(pod)
        // then
        verify(clients.get().pods().inNamespace(operator.namespace))
            .withName(pod.metadata.name)
    }

    @Test
    fun `#watchLog() is using resource namespace if exists`() {
        // given
        val pod = PodBuilder(POD2).build()
        clearInvocations(operator)
        // when
        operator.watch(pod, mock())
        // then
        verify(clients.get().pods().inNamespace(operator.namespace))
            .withName(pod.metadata.name)
    }

    @Test
    fun `#watchLog() is using operator namespace if resource namespace is null`() {
        // given
        val pod = PodBuilder(POD2).editMetadata()
            .withNamespace(null) // no namespace in pod
            .endMetadata()
            .build()
        operator.namespace =  "smurfington" // should use it
        clearInvocations(operator)
        // when
        operator.watchLog(pod, mock())
        // then
        verify(clients.get().pods().inNamespace(operator.namespace))
            .withName(pod.metadata.name)
    }

    @Test
    fun `#watchLog() is waiting for pod to become ready`() {
        // given
        // when
        operator.watchLog(POD2, mock())
        // then
        verify(clients.get().pods().inNamespace(POD2.metadata.namespace).withName(POD2.metadata.name))
            .waitUntilReady(any(), any())
    }

    class TestablePodsOperator(clients: Clients<KubernetesClient>): NamespacedPodsOperator(clients) {

        public override fun loadAllResources(namespace: String): List<Pod> {
            return super.loadAllResources(namespace)
        }
    }
}
