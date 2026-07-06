package com.consultorprocessos.crawler;

import com.consultorprocessos.shared.BaseIntegrationTest;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

public abstract class BaseProviderIT extends BaseIntegrationTest {

    @RegisterExtension
    static final WireMockExtension WIRE_MOCK =
            WireMockExtension.newInstance()
                    .options(wireMockConfig().dynamicPort())
                    .build();

    @DynamicPropertySource
    static void configureProviderUrls(DynamicPropertyRegistry registry) {
        String wireMockUrl = WIRE_MOCK.baseUrl();
        registry.add("app.courts.stf.base-url",   () -> wireMockUrl);
        registry.add("app.courts.eproc.base-url",  () -> wireMockUrl);
        registry.add("app.courts.stjrj.base-url",  () -> wireMockUrl);

    }
}