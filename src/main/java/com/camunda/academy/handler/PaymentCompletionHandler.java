package com.camunda.academy.handler;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.client.api.worker.JobHandler;

public class PaymentCompletionHandler implements JobHandler {
  private final ZeebeClient zeebeClient;

  public PaymentCompletionHandler(ZeebeClient zeebeClient) {
    this.zeebeClient = zeebeClient;
  }

  @Override
  public void handle(JobClient client, ActivatedJob job) throws Exception {
    String orderId = (String) job.getVariablesAsMap().get("orderId");
    zeebeClient
        .newPublishMessageCommand()
        .messageName("paymentCompletedMessage")
        .correlationKey(orderId)
        .send()
        .join();
    client.newCompleteCommand(job).send().join();
  }
}
