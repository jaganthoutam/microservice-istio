package com.ewolff.microservice.bonus;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.junit.Rule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.ewolff.microservice.bonus.poller.BonusPoller;

import au.com.dius.pact.consumer.Pact;
import au.com.dius.pact.consumer.dsl.DslPart;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit.PactProviderRule;
import au.com.dius.pact.consumer.junit.PactVerification;
import au.com.dius.pact.core.model.RequestResponsePact;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = BonusTestApp.class, webEnvironment = WebEnvironment.DEFINED_PORT)
@ActiveProfiles("test")
public class PollingTest {

	@Autowired
	private BonusRepository bonusRepository;

	@Autowired
	private BonusPoller bonusPoller;
	@Rule
	public PactProviderRule mockProvider = new PactProviderRule("OrderProvider", "localhost", 8081, this);

	private DslPart feedBody(Date now) {
		return new PactDslJsonBody().date("updated", "yyyy-MM-dd'T'kk:mm:ss.SSS+0000", now)
									.eachLike("orders")
									.numberType("id", 1)
									.stringType("link", "http://localhost:8081/order/1")
									.date("updated", "yyyy-MM-dd'T'kk:mm:ss.SSS+0000", now)
									.closeArray();
	}

	public DslPart order(Date now) {
		return new PactDslJsonBody()
									.numberType("id", 1)
									.numberType("numberOfLines", 1)
									.numberType("revenue", 42)
									.object("customer")
									.numberType("customerId", 1)
									.stringType("name", "Wolff")
									.stringType("firstname", "Eberhard")
									.stringType("email", "eberhard.wolff@posteo.net")
									.closeObject();
	}

	@Pact(consumer = "Invoice")
	public RequestResponsePact createFragment(PactDslWithProvider builder) {
		Map<String, String> headers = new HashMap<String, String>();
		headers.put("Content-Type", "application/json");
		Date now = new Date();
		return builder	.uponReceiving("Request for order feed")
						.method("GET")
						.path("/feed")
						.willRespondWith()
						.status(200)
						.headers(headers)
						.body(feedBody(now))
						.uponReceiving("Request for an order")
						.method("GET")
						.path("/order/1")
						.willRespondWith()
						.status(200)
						.headers(headers)
						.body(order(now))
						.toPact();
	}

	@Test
	@PactVerification
	public void orderArePolled() {
		long countBeforePoll = bonusRepository.count();
		bonusPoller.pollInternal();
		assertThat(bonusRepository.count(), is(greaterThan(countBeforePoll)));
		for (Bonus bonus : bonusRepository.findAll()) {
			assertThat(bonus.getRevenue(), is(greaterThan(0.0)));
		}
	}

}
