package com.camunda.academy.handler;

import com.camunda.academy.services.CreditCardService;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.client.api.worker.JobHandler;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreditCardChargingHandler implements JobHandler {
  private static final Logger LOG = LoggerFactory.getLogger(CreditCardChargingHandler.class);
  private final CreditCardService creditCardService;

  public CreditCardChargingHandler(CreditCardService creditCardService) {
    this.creditCardService = creditCardService;
  }

  @Override
  public void handle(JobClient client, ActivatedJob job) throws Exception {
    // extract data
    String cardNumber = (String) job.getVariablesAsMap().get("cardNumber");
    String cvc = (String) job.getVariablesAsMap().get("cvc");
    String expiryDate = (String) job.getVariablesAsMap().get("expiryDate");
    Double amount = (Double) job.getVariablesAsMap().get("openAmount");
    // execute business logic
    try {
      creditCardService.chargeAmount(cardNumber, cvc, expiryDate, amount);
    } catch (IllegalStateException e) {
      System.err.println(e.getMessage());
      client
          .newThrowErrorCommand(job)
          .errorCode("creditCardChargeError")
          .errorMessage(e.getMessage())
          .send()
          .join();
    }
    Map<String, Object> result = new HashMap<>();
    result.put("openAmount", 0);
    // return result
    client.newCompleteCommand(job).variables(result).send().join();
    // return results
    client.newCompleteCommand(job).send().join();
  }
}
