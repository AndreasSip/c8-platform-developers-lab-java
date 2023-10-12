package com.camunda.academy.services;

import org.springframework.stereotype.Service;

@Service
public class CreditCardService {

	public void chargeAmount(String cardNumber, String cvc, String expiryDate, Double amount) {
		System.out.printf("charging card %s that expires on %s and has cvc %s with amount of %f %s", cardNumber, expiryDate, cvc, amount, System.lineSeparator());
		if(expiryDate.length() != 5){
			throw new IllegalStateException(String.format("Expiry Date '%s' is invalid: Should be 5 characters long",expiryDate));
		}
		System.out.println("payment completed");

	}
}