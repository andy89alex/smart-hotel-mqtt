package com.example.smarthotel.dashboard;

import com.example.smarthotel.common.Topics;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter;
import org.springframework.messaging.MessageChannel;

@SpringBootApplication
public class DashboardApplication {
    public static void main(String[] args) {
        SpringApplication.run(DashboardApplication.class, args);
    }

    @Bean
    public MqttPahoClientFactory mqttClientFactory(@Value("${dashboard.brokerUrl}") String brokerUrl) {
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        MqttConnectOptions opts = new MqttConnectOptions();
        opts.setServerURIs(new String[]{brokerUrl});
        factory.setConnectionOptions(opts);
        return factory;
    }

    @Bean
    public MessageChannel mqttInboundChannel() { return new DirectChannel(); }

    @Bean
    public MqttPahoMessageDrivenChannelAdapter inbound(MqttPahoClientFactory factory) {
        MqttPahoMessageDrivenChannelAdapter adapter =
            new MqttPahoMessageDrivenChannelAdapter("dashboard-in", factory, Topics.ALL_WILDCARD);
        adapter.setQos(1);
        adapter.setOutputChannel(mqttInboundChannel());
        // MqttIngest.handle declares Message<byte[]>; the default converter delivers
        // String payloads, so force byte[] to match (and to survive the dashboard's
        // own outbound cmd/* publishes looping back through the hotel/# wildcard).
        DefaultPahoMessageConverter converter = new DefaultPahoMessageConverter();
        converter.setPayloadAsBytes(true);
        adapter.setConverter(converter);
        return adapter;
    }

    @Bean
    public MessageChannel mqttOutboundChannel() {
        return new org.springframework.integration.channel.DirectChannel();
    }

    @Bean
    @org.springframework.integration.annotation.ServiceActivator(inputChannel = "mqttOutboundChannel")
    public org.springframework.messaging.MessageHandler mqttOutbound(MqttPahoClientFactory factory) {
        var handler = new org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler("dashboard-out", factory);
        handler.setAsync(true);
        handler.setDefaultQos(1);
        handler.setTopicExpressionString("headers['mqtt_topic']");
        return handler;
    }
}
