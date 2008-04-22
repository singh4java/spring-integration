/*
 * Copyright 2002-2008 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.integration.bus;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

import org.springframework.integration.annotation.Handler;
import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.channel.MessageChannel;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.config.MessageEndpointAnnotationPostProcessor;
import org.springframework.integration.dispatcher.SynchronousChannel;
import org.springframework.integration.endpoint.HandlerEndpoint;
import org.springframework.integration.handler.MessageHandler;
import org.springframework.integration.message.Message;
import org.springframework.integration.message.MessagingException;
import org.springframework.integration.message.StringMessage;
import org.springframework.integration.scheduling.Subscription;

/**
 * @author Mark Fisher
 */
public class SynchronousChannelSubscriptionTests {

	private MessageBus bus = new MessageBus();

	private MessageChannel sourceChannel = new SynchronousChannel();

	private MessageChannel targetChannel = new SynchronousChannel();


	@Before
	public void setupChannels() {
		bus.registerChannel("sourceChannel", sourceChannel);
		bus.registerChannel("targetChannel", targetChannel);
	}


	@Test
	public void testSendAndReceiveForRegisteredEndpoint() {
		HandlerEndpoint endpoint = new HandlerEndpoint(new TestHandler());
		endpoint.setSubscription(new Subscription("sourceChannel"));
		endpoint.setDefaultOutputChannelName("targetChannel");
		bus.registerEndpoint("testEndpoint", endpoint);
		bus.start();
		this.sourceChannel.send(new StringMessage("foo"));
		Message<?> response = this.targetChannel.receive();
		assertEquals("foo!", response.getPayload());
		bus.stop();
	}

	@Test
	public void testSendAndReceiveForAnnotatedEndpoint() {
		MessageEndpointAnnotationPostProcessor postProcessor = new MessageEndpointAnnotationPostProcessor(bus);
		postProcessor.afterPropertiesSet();
		TestEndpoint endpoint = new TestEndpoint();
		postProcessor.postProcessAfterInitialization(endpoint, "testEndpoint");
		bus.start();
		this.sourceChannel.send(new StringMessage("foo"));
		Message<?> response = this.targetChannel.receive();
		assertEquals("foo-from-annotated-endpoint", response.getPayload());
		bus.stop();
	}

	@Test(expected=MessagingException.class)
	public void testExceptionThrownFromRegisteredEndpoint() {
		QueueChannel errorChannel = new QueueChannel();
		bus.setErrorChannel(errorChannel);		
		HandlerEndpoint endpoint = new HandlerEndpoint(new MessageHandler() {
			public Message<?> handle(Message<?> message) {
				throw new RuntimeException("intentional test failure");
			}
		});
		endpoint.setSubscription(new Subscription("sourceChannel"));
		endpoint.setDefaultOutputChannelName("targetChannel");
		bus.registerEndpoint("testEndpoint", endpoint);
		bus.start();
		this.sourceChannel.send(new StringMessage("foo"));
	}

	@Test(expected=MessagingException.class)
	public void testExceptionThrownFromAnnotatedEndpoint() {
		QueueChannel errorChannel = new QueueChannel();
		bus.setErrorChannel(errorChannel);
		MessageEndpointAnnotationPostProcessor postProcessor = new MessageEndpointAnnotationPostProcessor(bus);
		postProcessor.afterPropertiesSet();
		FailingTestEndpoint endpoint = new FailingTestEndpoint();
		postProcessor.postProcessAfterInitialization(endpoint, "testEndpoint");
		bus.start();
		this.sourceChannel.send(new StringMessage("foo"));
	}


	private static class TestHandler implements MessageHandler {

		public Message<?> handle(Message<?> message) {
			return new StringMessage(message.getPayload() + "!");
		}
	}


	@MessageEndpoint(input="sourceChannel", defaultOutput="targetChannel")
	public static class TestEndpoint {

		@Handler
		public Message<?> handle(Message<?> message) {
			return new StringMessage(message.getPayload() + "-from-annotated-endpoint");
		}
	}


	@MessageEndpoint(input="sourceChannel", defaultOutput="targetChannel")
	public static class FailingTestEndpoint {

		@Handler
		public Message<?> handle(Message<?> message) {
			throw new RuntimeException("intentional test failure");
		}
	}

}
