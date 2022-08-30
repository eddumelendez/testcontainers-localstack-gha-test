package org.example;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.CreateQueueResult;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

class LocalStackContainerTest {

    @ParameterizedTest(name = "localstack version {0}")
    @ValueSource(strings = {"0.11.3", "0.12.8", "0.13.0", "0.14.0", "0.14.1", "0.14.2", "0.14.3", "0.14.4", "0.14.5", "1.0.4"})
    void test(String tag) {
        try (LocalStackContainer localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack").withTag(tag))
                .withServices(LocalStackContainer.Service.SQS)
                .withEnv("DEBUG", "1")) {
            localstack.start();

            AmazonSQS sqs = AmazonSQSClientBuilder
                    .standard()
                    .withEndpointConfiguration(
                            new AwsClientBuilder.EndpointConfiguration(
                                    localstack.getEndpointOverride(LocalStackContainer.Service.SQS).toString(),
                                    localstack.getRegion()
                            )
                    )
                    .withCredentials(
                            new AWSStaticCredentialsProvider(
                                    new BasicAWSCredentials(localstack.getAccessKey(), localstack.getSecretKey())
                            )
                    )
                    .build();

            CreateQueueResult queueResult = sqs.createQueue("baz");
            String fooQueueUrl = queueResult.getQueueUrl();
            assertThat(fooQueueUrl)
                    .contains("http://" + localstack.getHost() + ":" + localstack.getMappedPort(4566));

            sqs.sendMessage(fooQueueUrl, "test");
            final long messageCount = sqs
                    .receiveMessage(fooQueueUrl)
                    .getMessages()
                    .stream()
                    .filter(message -> message.getBody().equals("test"))
                    .count();
            assertThat(messageCount).isOne();
        }
    }

}
