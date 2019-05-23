/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2019 manticorecao@gmail.com
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */


package io.skywalking.apm.plugin.jersey.v1;

import com.sun.jersey.api.client.ClientRequest;
import com.sun.jersey.api.client.ClientResponse;
import org.apache.skywalking.apm.agent.core.context.CarrierItem;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;

import java.lang.reflect.Method;
import java.net.URL;

/**
 * WebResourceHandleClassInterceptor
 *
 * @author caobin
 * @version 1.0 2019.05.22
 */
public class WebResourceHandleClassInterceptor implements InstanceMethodsAroundInterceptor {


    @Override
    public void beforeMethod(
            EnhancedInstance enhancedInstance,
            Method method, Object[] allArguments,
            Class<?>[] classes,
            MethodInterceptResult methodInterceptResult) throws Throwable {

        ClientRequest clientRequest = (ClientRequest)allArguments[1];
        URL url = clientRequest.getURI().toURL();
        ContextCarrier contextCarrier = new ContextCarrier();
        int port = url.getPort() == -1 ? 80 : url.getPort();
        String remotePeer = url.getHost() + ":" + port;
        String operationName = url.getPath();
        if (operationName == null || operationName.length() == 0) {
            operationName = "/";
        }
        AbstractSpan span = ContextManager.createExitSpan(operationName, contextCarrier, remotePeer);
        //TODO: 需扩展lib - jersey client, 现有jetty替代
        span.setComponent(ComponentsDefine.JETTY_CLIENT);
        Tags.HTTP.METHOD.set(span, clientRequest.getMethod());
        Tags.URL.set(span, url.toString());
        SpanLayer.asHttp(span);

        CarrierItem next = contextCarrier.items();
        while (next.hasNext()) {
            next = next.next();
            clientRequest.getHeaders().add(next.getHeadKey(), next.getHeadValue());
        }
    }

    @Override
    public Object afterMethod(
            EnhancedInstance enhancedInstance,
            Method method,
            Object[] objects,
            Class<?>[] classes,
            Object ret) throws Throwable {
        ClientResponse clientResponse = (ClientResponse)ret;
        if (clientResponse != null) {
            int statusCode = clientResponse.getStatus();
            AbstractSpan span = ContextManager.activeSpan();
            if (statusCode >= 400) {
                span.errorOccurred();
                Tags.STATUS_CODE.set(span, statusCode + "");
            }
        }
        ContextManager.stopSpan();
        return ret;
    }

    @Override
    public void handleMethodException(
            EnhancedInstance enhancedInstance,
            Method method,
            Object[] objects,
            Class<?>[] classes,
            Throwable throwable) {
        AbstractSpan activeSpan = ContextManager.activeSpan();
        activeSpan.log(throwable);
        activeSpan.errorOccurred();
    }
}
