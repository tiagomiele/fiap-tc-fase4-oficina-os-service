# Índice documentações de entrega — FIAP Tech Challenge Fase 4 | Oficina

Este índice reúne em um só lugar a documentação técnica, os requisitos obrigatórios e as principais evidências da Oficina Fase 4.

## Requisitos obrigatórios e evidências

O documento abaixo liga cada requisito obrigatório à sua implementação técnica e às evidências de execução.

Leia-o por inteiro para entender a solução completa. Ele é o guia principal de evidências da Fase 4.

- [Consultar os requisitos obrigatórios e as evidências](README-requisitos-obrigatorios-fase4.md)

## Vídeo de demonstração

O vídeo mostra o fluxo completo de uma OS, a Saga com tratamento de falhas, o deploy automatizado e o monitoramento dos fluxos distribuídos.

- Vídeo de demonstração da Fase 4: link a incluir depois da publicação, com duração de até 15 min.

## Documentação complementar

As pastas abaixo reúnem os diagramas de arquitetura, o contrato da Saga, os contratos de API e a documentação de cada componente.

- [Saga orquestrada: estratégia, fluxo e compensações](docs/saga/README.md)
- [Contrato das mensagens da Saga](docs/saga/contrato-mensagens.md)
- [Diagramas de arquitetura e da Saga](docs/assets/)
- [Cenários BDD da Saga](bdd/src/test/resources/features/saga.feature)
- [Collection Postman/Newman do fluxo E2E](tests/postman/oficina-weeks4-5.postman_collection.json)
- [Arquitetura do Auth](https://github.com/tiagomiele/fiap-tc-fase4-oficina-auth-serverless/blob/main/docs/arquitetura.md) · [Contrato OpenAPI do Auth](https://github.com/tiagomiele/fiap-tc-fase4-oficina-auth-serverless/blob/main/docs/openapi/oficina-auth.yaml)
- [Modelo relacional](https://github.com/tiagomiele/fiap-tc-fase4-oficina-database-infra/blob/main/docs/modelo-relacional.md) · [ADRs dos bancos](https://github.com/tiagomiele/fiap-tc-fase4-oficina-database-infra/tree/main/docs/adr)
- [Infraestrutura e observabilidade](https://github.com/tiagomiele/fiap-tc-fase4-oficina-kubernetes-infra/blob/main/docs/infraestrutura-observabilidade.md) · [Fluxo de CI/CD](https://github.com/tiagomiele/fiap-tc-fase4-oficina-kubernetes-infra/blob/main/docs/cicd.md)

## Bootstrap das aplicações

Os guias abaixo mostram como configurar, implantar e destruir os seis projetos na AWS.

- [Bootstrap da solução na AWS](docs/bootstrap-aws-do-zero-fase4.md)
- [Bootstrap automatizado: subir, retomar depois do Start Lab, validar, Mercado Pago e destruir com scripts e menus](docs-fase4/bootstrap-automatizado-fase4.md)
