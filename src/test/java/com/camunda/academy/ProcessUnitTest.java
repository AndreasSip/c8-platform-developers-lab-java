package com.camunda.academy;

import com.camunda.academy.handler.PaymentInvocationHandler;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.ActivateJobsResponse;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.response.DeploymentEvent;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.process.test.api.ZeebeTestEngine;
import io.camunda.zeebe.process.test.extension.ZeebeProcessTest;
import io.camunda.zeebe.process.test.filters.RecordStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import static io.camunda.zeebe.process.test.assertions.BpmnAssert.*;
import static org.assertj.core.api.Assertions.*;

@ZeebeProcessTest
public class ProcessUnitTest {
  private ZeebeTestEngine engine;
  private ZeebeClient client;
  private RecordStream recordStream;

  @BeforeEach
  void setup() {
    DeploymentEvent deploymentEvent =
        client
            .newDeployResourceCommand()
            .addResourceFromClasspath("bestellprozess.bpmn")
            .addResourceFromClasspath("bezahlungsprozess.bpmn")
            .send()
            .join();
  }

  @Test
  void shouldRunHappyPath() throws Exception {
    ProcessInstanceEvent processInstanceEvent = client
        .newCreateInstanceCommand()
        .bpmnProcessId("orderProcess")
        .latestVersion()
        .variables(
            "{\"orderTotal\": 45.99, \"customerId\": \"cust30\", \"cardNumber\": \"1234 5678\", \"cvc\": \"123\", \"expiryDate\": \"09/24\"}")
        .send()
        .join();
    engine.waitForIdleState(Duration.ofSeconds(10));
    assertThat(processInstanceEvent).isActive().isWaitingAtElements("Activity_07hr7v5");
    ActivateJobsResponse jobs = client
        .newActivateJobsCommand()
        .jobType("payment-invocation")
        .maxJobsToActivate(1)
        .send()
        .join();
    assertThat(jobs.getJobs()).hasSize(1);
    ActivatedJob job = jobs.getJobs().get(0);
    String orderId = UUID.randomUUID().toString();
    //    PaymentInvocationHandler paymentInvocationHandler = new PaymentInvocationHandler(client);
    //    paymentInvocationHandler.handle(client,job);
    client.newCompleteCommand(job).variables(Map.of("orderId",orderId)).send().join();
    engine.waitForIdleState(Duration.ofSeconds(10));
    client.newPublishMessageCommand().messageName("paymentCompletedMessage").correlationKey(orderId).send().join();
    engine.waitForIdleState(Duration.ofSeconds(10));
    assertThat(processInstanceEvent).isCompleted();
  }
}
