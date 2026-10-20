package br.com.qsoft.governance.agentpolicies.infrastructure.bootstrap;

import br.com.qsoft.governance.agentpolicies.application.port.DemoDirectoryLookup;
import br.com.qsoft.governance.agentpolicies.application.port.KnowledgeSeederRepository;
import br.com.qsoft.governance.agentpolicies.domain.Chunking;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/**
 * Seeder de demonstração do conhecimento (SPEC-05 §5). Idempotente por título,
 * roda após o contexto subir (depois do seeder do IAM) e usa textos originais
 * em pt-BR, divididos em trechos por parágrafo.
 * <p>
 * Os documentos departamentais usam o departamento homônimo quando ele existe;
 * caso contrário são criados como {@code tenant_wide} (o seed do IAM não cria
 * departamentos) — escolha registrada em log.
 */
@Component
@ConditionalOnProperty(name = "app.bootstrap.demo-data", havingValue = "true")
public class AgentPoliciesDemoSeeder implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(AgentPoliciesDemoSeeder.class);

    static final String MARKETING = "Marketing";
    static final String ENGINEERING = "Engenharia";

    static final String POLICY_AI_USE = "Política de Uso de IA Generativa";
    static final String POLICY_LGPD = "Dados Pessoais e LGPD no uso de IA";
    static final String POLICY_MARKETING = "Diretrizes de IA para Marketing";
    static final String POLICY_ENGINEERING = "Diretrizes de IA para Engenharia";

    private final KnowledgeSeederRepository repository;
    private final DemoDirectoryLookup directory;
    private final String tenantSlug;

    public AgentPoliciesDemoSeeder(KnowledgeSeederRepository repository,
                                   DemoDirectoryLookup directory,
                                   @Value("${app.bootstrap.demo-tenant-slug:qsoft}") String tenantSlug) {
        this.repository = repository;
        this.directory = directory;
        this.tenantSlug = tenantSlug;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        seed();
    }

    /** Executa o seed de forma idempotente; chamado pelo evento e por testes. */
    public void seed() {
        directory.findTenantIdBySlug(tenantSlug).ifPresentOrElse(
            this::seedTenant,
            () -> log.info("Tenant demo '{}' ausente; nenhum documento de políticas criado.",
                tenantSlug));
    }

    void seedTenant(UUID tenantId) {
        TenantId tenant = TenantId.of(tenantId);
        seedIfAbsent(tenant, POLICY_AI_USE, true, Set.of(), AI_USE_TEXT);
        seedIfAbsent(tenant, POLICY_LGPD, true, Set.of(), LGPD_TEXT);

        Set<UUID> marketing = directory.findDepartmentIdByName(tenantId, MARKETING)
            .map(Set::of).orElse(Set.of());
        seedIfAbsent(tenant, POLICY_MARKETING, marketing.isEmpty(), marketing, MARKETING_TEXT);
        if (marketing.isEmpty()) {
            log.info("Departamento '{}' ausente; '{}' criado como tenant_wide.", MARKETING,
                POLICY_MARKETING);
        }

        Set<UUID> engineering = directory.findDepartmentIdByName(tenantId, ENGINEERING)
            .map(Set::of).orElse(Set.of());
        seedIfAbsent(tenant, POLICY_ENGINEERING, engineering.isEmpty(), engineering,
            ENGINEERING_TEXT);
        if (engineering.isEmpty()) {
            log.info("Departamento '{}' ausente; '{}' criado como tenant_wide.", ENGINEERING,
                POLICY_ENGINEERING);
        }
    }

    private void seedIfAbsent(TenantId tenant, String title, boolean tenantWide,
                              Set<UUID> departmentIds, String text) {
        if (repository.existsByTitle(tenant, title)) {
            return;
        }
        List<String> chunks = Chunking.split(text);
        repository.seed(tenant, title, tenantWide, departmentIds, chunks);
        log.info("Documento de políticas '{}' criado com {} trecho(s).", title, chunks.size());
    }

    private static final String AI_USE_TEXT = """
        O uso de IA generativa na empresa deve apoiar o trabalho, nunca substituir a análise humana em decisões que afetem pessoas. Antes de usar uma ferramenta, verifique se ela foi homologada pelo time de tecnologia e se o modelo está previsto na política do seu departamento.

        É proibido inserir dados pessoais, credenciais, segredos comerciais ou trechos de contratos em ferramentas não homologadas. Quando a tarefa exigir dados sensíveis, utilize apenas os ambientes aprovados e minimize a informação enviada.

        Toda resposta gerada por IA deve ser revisada antes de ser compartilhada externamente. O usuário continua responsável pelo conteúdo final, inclusive por erros, omissões ou vieses.

        Dúvidas sobre ferramentas, modelos e limites de uso devem ser encaminhadas ao administrador do tenant e ao time de segurança da informação.
        """;

    private static final String LGPD_TEXT = """
        O tratamento de dados pessoais em ferramentas de IA deve observar a LGPD. Informe apenas o necessário para a finalidade pretendida e evite incluir nomes, documentos, endereços ou dados de saúde quando não forem indispensáveis.

        Antes de enviar dados de clientes ou colaboradores, verifique a base legal e o contrato com o fornecedor. Ferramentas que treinam modelos com os dados enviados não devem ser usadas para informações confidenciais.

        Registros de uso operacional guardam metadados, como tokens e custo, mas não o conteúdo das mensagens. Em caso de incidente, acione o encarregado de dados e preserve as evidências técnicas.

        O descarte de conversas e documentos deve seguir a política de retenção vigente. Não exporte dados para dispositivos pessoais nem os compartilhe em canais não corporativos.
        """;

    private static final String MARKETING_TEXT = """
        Campanhas e textos criados com apoio de IA devem ser revisados por uma pessoa do time antes da publicação. Verifique tom de voz, promessas comerciais e adequação à marca.

        Não utilize dados de desempenho de campanhas de clientes em ferramentas externas sem autorização. Métricas agregadas podem ser usadas para análise interna, desde que não identifiquem pessoas.

        Toda peça que cite preços, prazos ou condições deve passar pela aprovação do responsável comercial. A IA pode sugerir variações, mas a decisão final é humana.
        """;

    private static final String ENGINEERING_TEXT = """
        Código sugerido por IA deve ser tratado como rascunho: revise, teste e garanta que não introduza vulnerabilidades ou dependências não aprovadas. Nunca cole credenciais, chaves ou trechos de produção em ferramentas externas.

        O uso de IA para análise de logs deve respeitar a minimização de dados. Remova identificadores pessoais antes de enviar amostras e prefira ambientes internos quando o volume for sensível.

        Mudanças geradas com apoio de IA seguem o mesmo fluxo de revisão e CI do restante do código. O autor do pull request permanece responsável pelo diff e pela documentação.
        """;
}
