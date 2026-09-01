package com.alpeca.wallet.ledger.config.rabbit;

import com.alpeca.wallet.ledger.event.WalletUpdatedEvent;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.ClassMapper;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ message infrastructure configuration.
 */
@Configuration
class RabbitMqMessageConfig {

    @Bean
    MessageConverter rabbitMessageConverter() {
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter();
        converter.setClassMapper(new WalletUpdatedEventClassMapper());
        return converter;
    }

    @Bean
    RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter rabbitMessageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(rabbitMessageConverter);
        return rabbitTemplate;
    }

    private static class WalletUpdatedEventClassMapper implements ClassMapper {

        @Override
        public void fromClass(Class<?> clazz, MessageProperties properties) {
            // Type metadata is sent through the custom "type" header.
        }

        @Override
        public Class<?> toClass(MessageProperties properties) {
            return WalletUpdatedEvent.class;
        }
    }
}
