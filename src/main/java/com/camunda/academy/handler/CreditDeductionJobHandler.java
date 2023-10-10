package com.camunda.academy.handler;

import com.camunda.academy.services.CustomerService;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.client.api.worker.JobHandler;
import java.util.HashMap;
import java.util.Map;

public class CreditDeductionJobHandler implements JobHandler {
  private final CustomerService customerService;

  public CreditDeductionJobHandler(CustomerService customerService) {
    this.customerService = customerService;
  }

  @Override
  public void handle(JobClient client, ActivatedJob job) throws Exception {
    // extract variables
    String customerId = (String) job.getVariablesAsMap().get("customerId");
    Double amount = (Double) job.getVariablesAsMap().get("orderTotal");
    // execute business logic
    Double openAmount = customerService.deductCredit(customerId, amount);
    // define result
    Map<String, Object> result = new HashMap<>();
    result.put("openAmount", openAmount);
    // return result
    client.newCompleteCommand(job).variables(result).send().join();
  }
}
