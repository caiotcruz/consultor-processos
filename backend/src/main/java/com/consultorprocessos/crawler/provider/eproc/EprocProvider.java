package com.consultorprocessos.crawler.provider.eproc;

import com.consultorprocessos.crawler.model.CrawlerSnapshot;
import com.consultorprocessos.crawler.provider.CourtProvider;
import com.consultorprocessos.crawler.service.CrawlerPipeline;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class EprocProvider implements CourtProvider {

    private static final String COURT_CODE = "EPROC";

    private static final String CONTROLLER_PATH = "/eproc/externo_controlador.php";

    private final CrawlerPipeline pipeline;
    private final EprocParser      parser;

    @Value("${app.courts.eproc.base-url}")
    private String eprocBaseUrl;

    @Override
    public CrawlerSnapshot consultar(String processNumber) {
        String formUrl = eprocBaseUrl + CONTROLLER_PATH
                + "?acao=processo_consulta_publica"
                + "&acao_origem=principal"
                + "&acao_retorno=processo_consulta_publica";

        Map<String, String> formData = buildFormData(processNumber);

        log.debug("[eProc] Consultando via POST: processo={} url={}",
                processNumber, formUrl);

        return pipeline.executePostForm(COURT_CODE, processNumber, formUrl, formData, parser);
    }

    private Map<String, String> buildFormData(String processNumber) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("acao",                  "processo_consulta_publica");
        data.put("acao_origem",           "principal");
        data.put("acao_retorno",          "processo_consulta_publica");
        data.put("txtNumProcesso",        processNumber);
        data.put("txtNumChave",           "");
        data.put("txtNumChaveDocumento",  "");
        data.put("txtStrParte",           "");
        data.put("chkFonetica",           "N");
        data.put("rdoTipo",               "CPF");
        data.put("txtCpfCnpj",            "");
        data.put("txtStrOAB",             "");
        return data;
    }

    @Override
    public String getCourtCode() { return COURT_CODE; }
}