![Java CI with Maven](https://github.com/devplatform-gamification/bot-apoiador-requisitante/workflows/Java%20CI%20with%20Maven/badge.svg?branch=master)

# bot-apoiador-requisitante
Bot para gerenciar a identificação de apoiador requisitante nas issues do jira.
- jira -- issue created + issue updated

## Docker image

`$ echo [pass] | sudo docker login docker.pkg.github.com -u [user] --password-stdin`

`$ sudo docker pull docker.pkg.github.com/devplatform-gamification/bot-apoiador-requisitante/bot-apoiador-requisitante:latest`

`$ sudo docker run docker.pkg.github.com/devplatform-gamification/bot-apoiador-requisitante/bot-apoiador-requisitante:latest -p 8901:8901`

## Environment variables

- RABBITMQ_HOST 	[rabbit-host] # default: localhost
- RABBIT_VHOST 	[rabbit-vhost] # default: /
- RABBIT_USERNAME 	[rabbit-user] # default: guest
- RABBIT_PASSWORD 	[rabbit-pass] # default: guest
- RABBIT_EXCHANGE 	[rabbit-exchange] # default: dev-platform.exchange
- RABBIT_QUEUE [service-default-queue] # default: apoiador-requisitante.queue
- RABBIT_PREFETCH   [rabbit-prefetch-messages] # default: 20
- RABBIT_CONCURRENCY [rabbit-concurrency] # default: 3
- RABBIT_MAX_CONCURRENCY [rabbit-max-concurrency] # default: 10

- APP_PORT [APP-PORT] # default: 8901
- RABBIT_JIRA_ISSUE_CREATED_ROUTINGKEY_PREFIX [jira-issuecreated-routingkey-prefix] # default: dev-platform.jira.ISSUE_CREATED.*
- RABBIT_JIRA_COMMENT_CREATED_ROUTINGKEY_PREFIX [jira-issueupdated-routingkey-prefix] # default: dev-platform.jira.ISSUE_UPDATED.*

- SERVICE_JIRA_URL [client-jira-url] # default: my-jira-url
- SERVICE_JIRA_USER [client-jira-user] # default: my-user
- SERVICE_JIRA_PASSWORD [client-jira-pass] # default: my-pass

- SERVICE_GITLAB_URL [client-gitlab-url] # default: my-gitlab-url
- SERVICE_GITLAB_TOKEN [client-gitlab-token] # default: my-gitlab-token

- SERVICE_TELEGRAM_TOKEN [client-telegram-token] # default: my-telegram-bot-token
	
