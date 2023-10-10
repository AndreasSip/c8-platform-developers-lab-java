package com.camunda.academy;

import com.camunda.academy.handler.CreditCardChargingHandler;
import com.camunda.academy.handler.CreditDeductionJobHandler;
import com.camunda.academy.handler.YourJobHandler;
import com.camunda.academy.services.CreditCardService;
import com.camunda.academy.services.CustomerService;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.worker.JobWorker;
import io.camunda.zeebe.client.impl.oauth.OAuthCredentialsProvider;
import io.camunda.zeebe.client.impl.oauth.OAuthCredentialsProviderBuilder;
import java.util.Scanner;

public class PaymentApplication {

  // Zeebe Client Credentials
  private static final String ZEEBE_ADDRESS =
      "b7acd461-929f-4a58-91f2-a347d95fa429.jfk-1.zeebe.camunda.io";
  private static final String ZEEBE_CLIENT_ID = "70.BhDa929_gzkRzqxOBNcL1LRjhU-o4";
  private static final String ZEEBE_CLIENT_SECRET =
      "J-5FxHsZozuVcg4YUCp-aew~CugN~qfh8SkrjxolJLtOi17U_FQB8ZGRZ7~-HCni";
  private static final String ZEEBE_AUTHORIZATION_SERVER_URL =
      "https://login.cloud.camunda.io/oauth/token";
  private static final String ZEEBE_TOKEN_AUDIENCE = "zeebe.camunda.io";

  public static void main(String[] args) {

    final OAuthCredentialsProvider credentialsProvider =
        new OAuthCredentialsProviderBuilder()
            .authorizationServerUrl(ZEEBE_AUTHORIZATION_SERVER_URL)
            .audience(ZEEBE_TOKEN_AUDIENCE)
            .clientId(ZEEBE_CLIENT_ID)
            .clientSecret(ZEEBE_CLIENT_SECRET)
            .build();

    try (final ZeebeClient client =
        ZeebeClient.newClientBuilder()
            .gatewayAddress(ZEEBE_ADDRESS)
            .credentialsProvider(credentialsProvider)
            .build()) {

      // Request the Cluster Topology
      System.out.println("Connected to: " + client.newTopologyRequest().send().join());

      // Start a Job Worker
      final JobWorker creditDeductionWorker =
          client
              .newWorker()
              .jobType("credit-deduction")
              .handler(new CreditDeductionJobHandler(new CustomerService()))
              .open();
      final JobWorker creditCardChargingWorker =
          client
              .newWorker()
              .jobType("credit-card-charging")
              .handler(new CreditCardChargingHandler(new CreditCardService()))
              .open();

      // Terminate the worker with an Integer input
      Scanner sc = new Scanner(System.in);
      sc.nextInt();
      sc.close();
      creditDeductionWorker.close();
      creditCardChargingWorker.close();

    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
