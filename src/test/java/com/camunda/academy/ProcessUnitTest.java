package com.camunda.academy;

import static io.camunda.zeebe.process.test.assertions.BpmnAssert.*;
import static org.assertj.core.api.Assertions.*;

import com.camunda.academy.handler.JobHandlerController.PaymentProcessVariables;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.ActivateJobsResponse;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.response.DeploymentEvent;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.client.api.response.PublishMessageResponse;
import io.camunda.zeebe.process.test.api.ZeebeTestEngine;
import io.camunda.zeebe.process.test.assertions.ProcessInstanceAssert;
import io.camunda.zeebe.process.test.extension.ZeebeProcessTest;
import io.camunda.zeebe.process.test.filters.RecordStream;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.camunda.community.process_test_coverage.junit5.platform8.ProcessEngineCoverageExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ZeebeProcessTest
@ExtendWith(ProcessEngineCoverageExtension.class)
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
  void shouldRunHappyPathOrderProcess() throws Exception {
    ProcessInstanceEvent processInstanceEvent =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId("orderProcess")
            .latestVersion()
            .variables(
                "{\"orderTotal\": 45.99, \"customerId\": \"cust30\", \"cardNumber\": \"1234 5678\", \"cvc\": \"123\", \"expiryDate\": \"09/24\"}")
            .send()
            .join();
    engine.waitForIdleState(Duration.ofSeconds(10));
    assertThat(processInstanceEvent).isActive().isWaitingAtElements("Activity_07hr7v5");
    ActivatedJob job = getJob("payment-invocation");
    String orderId = UUID.randomUUID().toString();
    //    PaymentInvocationHandler paymentInvocationHandler = new PaymentInvocationHandler(client);
    //    paymentInvocationHandler.handle(client,job);
    client.newCompleteCommand(job).variables(Map.of("orderId", orderId)).send().join();
    engine.waitForIdleState(Duration.ofSeconds(10));
    client
        .newPublishMessageCommand()
        .messageName("paymentCompletedMessage")
        .correlationKey(orderId)
        .send()
        .join();
    engine.waitForIdleState(Duration.ofSeconds(10));
    assertThat(processInstanceEvent).isCompleted();
  }

  @Test
  void shouldRunHappyPathPaymentProcess() throws Exception {
    PaymentProcessVariables variables =
        new PaymentProcessVariables(
            45.99, "cust30", "1234 5678", "789", "07/24", null, UUID.randomUUID().toString());
    PublishMessageResponse paymentRequestMessage =
        client
            .newPublishMessageCommand()
            .messageName("paymentRequestMessage")
            .correlationKey(variables.orderId())
            .variables(variables)
            .send()
            .join();
    engine.waitForIdleState(Duration.ofSeconds(10));
    ProcessInstanceAssert assertion = assertThat(paymentRequestMessage).extractingProcessInstance();
    assertion.isActive().isWaitingAtElements("Activity_19pwomx");
    ActivatedJob job = getJob("credit-deduction");
    Double openAmount = 45.99 - 30.0;
    client.newCompleteCommand(job).variables(Map.of("openAmount", openAmount)).send().join();
    engine.waitForIdleState(Duration.ofSeconds(10));
    assertion.isWaitingAtElements("Activity_1mon74s");
    job = getJob("credit-card-charging");
    client.newCompleteCommand(job).send().join();
    engine.waitForIdleState(Duration.ofSeconds(10));
    assertion.isWaitingAtElements("Event_11n20ym");
    job = getJob("payment-completion");
    client.newCompleteCommand(job).send().join();
    engine.waitForIdleState(Duration.ofSeconds(10));
    assertion.isCompleted();
  }

  @Test
  void shouldRunNoOpenAmountPaymentProcess() throws Exception {
    ProcessInstanceEvent processInstanceEvent =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId("paymentProcess")
            .latestVersion()
            .variables(
                new PaymentProcessVariables(
                    29d, "cust30", "1234 5678", "789", "07/24", 0d, UUID.randomUUID().toString()))
            .startBeforeElement("Gateway_1tqlhkt")
            .send()
            .join();
    engine.waitForIdleState(Duration.ofSeconds(10));
    assertThat(processInstanceEvent).isActive().isWaitingAtElements("Event_11n20ym");
    client.newCancelInstanceCommand(processInstanceEvent.getProcessInstanceKey()).send().join();
    engine.waitForIdleState(Duration.ofSeconds(10));
  }



  private ActivatedJob getJob(String jobType) {
    ActivateJobsResponse jobs =
        client.newActivateJobsCommand().jobType(jobType).maxJobsToActivate(1).send().join();
    assertThat(jobs.getJobs()).hasSize(1);
    return jobs.getJobs().get(0);
  }
}
