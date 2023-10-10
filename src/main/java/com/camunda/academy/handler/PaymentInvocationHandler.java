package com.camunda.academy.handler;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.client.api.worker.JobHandler;
import java.util.Map;
import java.util.UUID;

public class PaymentInvocationHandler implements JobHandler {
  private final ZeebeClient zeebeClient;

  public PaymentInvocationHandler(ZeebeClient zeebeClient) {
    this.zeebeClient = zeebeClient;
  }

  @Override
  public void handle(JobClient client, ActivatedJob job) throws Exception {
    String orderId = UUID.randomUUID().toString();
    Map<String, Object> variablesAsMap = job.getVariablesAsMap();
    variablesAsMap.put("orderId",orderId);
    zeebeClient
        .newPublishMessageCommand()
        .messageName("paymentRequestMessage")
        .correlationKey(orderId)
        .variables(variablesAsMap)
        .send()
        .join();
    client.newCompleteCommand(job).variables(Map.of("orderId", orderId)).send().join();
  }
}
