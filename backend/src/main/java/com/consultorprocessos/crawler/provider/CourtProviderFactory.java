package com.consultorprocessos.crawler.provider;

import com.consultorprocessos.crawler.exception.ProviderNotFoundException;
import com.consultorprocessos.crawler.provider.mock.MockCourtProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@Slf4j
public class CourtProviderFactory {

    private final Map<String, CourtProvider> providers;
    private final MockCourtProvider          mockProvider;

    public CourtProviderFactory(
            List<CourtProvider> providerList,
            @Autowired(required = false) MockCourtProvider mockProvider) {

        this.mockProvider = mockProvider;

        this.providers = providerList.stream()
                .filter(p -> !(p instanceof MockCourtProvider))
                .collect(Collectors.toMap(
                        CourtProvider::getCourtCode,
                        Function.identity()));

        log.info("CourtProviderFactory inicializado. Providers registrados: {}. " +
                 "MockProvider ativo: {}",
                providers.keySet(),
                mockProvider != null);
    }

    public CourtProvider getProvider(String courtCode) {
        if (mockProvider != null) {
            log.debug("DEV: redirecionando consulta de '{}' para MockCourtProvider.", courtCode);
            return mockProvider;
        }

        CourtProvider provider = providers.get(courtCode);
        if (provider == null) {
            throw new ProviderNotFoundException(courtCode);
        }
        return provider;
    }
}