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

import org.apache.skywalking.apm.agent.core.context.CarrierItem;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.OfficialComponent;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;

/**
 * ServletContainerInterceptor
 *
 * @author caobin
 * @version 1.0 2019.05.22
 */
public class ServletContainerInterceptor implements InstanceMethodsAroundInterceptor {

    private static final OfficialComponent JERSEY_SERVER = new OfficialComponent(61, "Jersey-Server");

    @Override
    public void beforeMethod(
            EnhancedInstance enhancedInstance,
            Method method,
            Object[] allArguments,
            Class<?>[] classes,
            MethodInterceptResult methodInterceptResult) throws Throwable {

        HttpServletRequest httpServletRequest = (HttpServletRequest) allArguments[0];

        ContextCarrier contextCarrier = new ContextCarrier();
        CarrierItem next = contextCarrier.items();
        while (next.hasNext()) {
            next = next.next();
            next.setHeadValue(httpServletRequest.getHeader(next.getHeadKey()));
        }

        AbstractSpan span = ContextManager.createEntrySpan(httpServletRequest.getRequestURI(), contextCarrier);
        Tags.URL.set(span, httpServletRequest.getRequestURI());
        Tags.HTTP.METHOD.set(span, httpServletRequest.getMethod());
        //需在服务端的配置component-libraries.yml中添加对应序号
        span.setComponent(JERSEY_SERVER);
        SpanLayer.asHttp(span);

    }

    @Override
    public Object afterMethod(
            EnhancedInstance enhancedInstance,
            Method method,
            Object[] allArguments,
            Class<?>[] classes,
            Object ret) throws Throwable {

        HttpServletResponse httpServletResponse = (HttpServletResponse) allArguments[1];
        AbstractSpan span = ContextManager.activeSpan();

        if (httpServletResponse.getStatus() >= 400) {
            span.errorOccurred();
            Tags.STATUS_CODE.set(span, Integer.toString(httpServletResponse.getStatus()));
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

        ContextManager.activeSpan().errorOccurred().log(throwable);
    }
}
