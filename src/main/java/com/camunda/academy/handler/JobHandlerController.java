package com.camunda.academy.handler;

import com.camunda.academy.services.CreditCardService;
import com.camunda.academy.services.CustomerService;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.spring.client.annotation.JobWorker;
import io.camunda.zeebe.spring.client.annotation.VariablesAsType;
import io.camunda.zeebe.spring.client.exception.ZeebeBpmnError;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.UUID;

@Component
public class JobHandlerController {
  private final CreditCardService creditCardService;
  private final CustomerService customerService;
  private final ZeebeClient zeebeClient;

  @Autowired
  public JobHandlerController(CreditCardService creditCardService, CustomerService customerService, ZeebeClient zeebeClient) {
    this.creditCardService = creditCardService;
    this.customerService = customerService;
    this.zeebeClient = zeebeClient;
  }

  @JobWorker(type = "credit-card-charging")
  public void creditCardCharging(@VariablesAsType PaymentProcessVariables variables) {
    try {
      creditCardService.chargeAmount(
          variables.cardNumber(), variables.cvc(), variables.expiryDate(), variables.openAmount());
    } catch (IllegalStateException e) {
      throw new ZeebeBpmnError("creditCardChargeError", e.getMessage(), Collections.emptyMap());
    }
  }

  @JobWorker(type = "payment-invocation")
  public OrderProcessVariables paymentInvocation(@VariablesAsType OrderProcessVariables variables) {
    String orderId = UUID.randomUUID().toString();
    zeebeClient
        .newPublishMessageCommand()
        .messageName("paymentRequestMessage")
        .correlationKey(orderId)
        .variables(
            new PaymentProcessVariables(
                variables.orderTotal(),
                variables.customerId(),
                variables.cardNumber(),
                variables.cvc(),
                variables.expiryDate(),
                null,
                orderId))
        .send()
        .join();
    return new OrderProcessVariables(null, null, null, null, null, orderId);
  }

  @JobWorker(type = "credit-deduction")
  public PaymentProcessVariables creditDeduction(@VariablesAsType PaymentProcessVariables variables){
    Double openAmount = customerService.deductCredit(variables.customerId(), variables.orderTotal());
    return new PaymentProcessVariables(null,null,null,null,null,openAmount,null);
  }

  @JobWorker(type = "payment-completion")
  public void paymentCompletion(@VariablesAsType PaymentProcessVariables variables){
    zeebeClient
        .newPublishMessageCommand()
        .messageName("paymentCompletedMessage")
        .correlationKey(variables.orderId())
        .send()
        .join();
  }

  @JsonInclude(Include.NON_NULL)
  public record OrderProcessVariables(
      Double orderTotal,
      String customerId,
      String cardNumber,
      String cvc,
      String expiryDate,
      String orderId) {}

  @JsonInclude(Include.NON_NULL)
  public record PaymentProcessVariables(
      Double orderTotal,
      String customerId,
      String cardNumber,
      String cvc,
      String expiryDate,
      Double openAmount,
      String orderId) {}
}
