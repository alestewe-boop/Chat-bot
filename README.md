# Balada CP — App de Chatbot para Android

Chatbot para Android, tela cheia, modo claro forçado e a bandeira da França
como símbolo principal (na barra superior e no ícone do app).

O bot responde de duas formas:

- **Com IA (recomendado):** conectado à API gratuita do Gemini (Google), responde
  qualquer pergunta corretamente, já sabendo os dados da Balada CP (horário,
  preço, endereço).
- **Sem IA (modo reserva):** se você não configurar uma chave, ou se a internet
  falhar, o bot usa uma lista fixa de palavras-chave (`RuleEngine` no código).

## Como ativar as respostas com IA

1. Gere uma chave gratuita em **[aistudio.google.com/apikey](https://aistudio.google.com/apikey)**
   (entre com uma conta Google e clique em "Create API key").
2. Abra o app Balada CP, toque no ícone **⚙** no canto superior direito.
3. Cole a chave e toque em **Salvar**. Pronto — o bot já passa a responder com IA.
4. A chave fica salva só no aparelho (não é enviada pro GitHub nem aparece no código).

Para editar as informações fixas que a IA usa sobre a casa (horário, preço,
endereço), procure a constante `CONTEXTO_LOCAL` dentro de `MainActivity.kt`.

## Como gerar o APK sem computador (pelo celular, via GitHub Actions)

Este projeto já vem com um "robô" configurado (`.github/workflows/build.yml`)
que compila o APK automaticamente na nuvem assim que você envia o código
pro GitHub. Dá pra fazer tudo pelo navegador do celular:

1. Crie uma conta gratuita em **github.com** (pelo navegador do celular).
2. Toque em **"New repository"** (novo repositório), dê um nome (ex: `balada-cp`)
   e deixe como **Public**. Crie sem adicionar README (o projeto já tem um).
3. Na página do repositório vazio, toque em **"uploading an existing file"**
   (ou "Add file → Upload files").
4. Extraia o `BaladaCP.zip` no celular (use um app de arquivos, tipo "Files"
   ou "ZArchiver" no Android) e envie **todos os arquivos e pastas** da
   pasta `BaladaCP` pra essa tela de upload (a maioria dos navegadores
   permite selecionar a pasta inteira). Toque em **"Commit changes"**.
5. Vá na aba **"Actions"** do repositório. Vai aparecer um workflow
   chamado **"Build APK"** rodando (ou toque em "Run workflow" se não
   iniciar sozinho). Espere uns 3–5 minutos.
6. Quando o status ficar verde (✅), toque no workflow concluído → role até
   **"Artifacts"** → baixe **"BaladaCP-apk"** (vem como um `.zip`).
7. Extraia esse zip no celular — dentro está o `app-debug.apk`.
8. Toque nele pra instalar (o Android pode pedir pra permitir "instalar de
   fontes desconhecidas" — autorize apenas para essa instalação).

Pronto, o app "Balada CP" estará instalado no seu celular.

## Como abrir e gerar o APK (com computador, opcional)

1. Baixe e extraia esta pasta no seu computador.
2. Abra o **Android Studio** (versão recente) → "Open" → selecione a pasta `BaladaCP`.
3. Aguarde o Gradle sincronizar (primeira vez baixa as dependências, precisa de internet).
4. Para testar: clique em "Run ▶" com um emulador ou celular conectado.
5. Para gerar o instalável: menu **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
   O arquivo `.apk` fica em `app/build/outputs/apk/debug/app-debug.apk`.
6. Copie o `.apk` para o celular e instale (pode ser preciso permitir
   "instalar de fontes desconhecidas" nas configurações do Android).

## Como editar as respostas do chatbot

Abra o arquivo:
`app/src/main/java/com/baladacp/app/MainActivity.kt`

Procure o objeto `RuleEngine`. Cada linha é um grupo de palavras-chave e a
resposta que o bot deve dar quando alguma delas aparecer na mensagem do
usuário. Exemplo:

```kotlin
listOf("ingresso", "entrada", "preço") to
    "Os ingressos custam a partir de R$ 30 na lista..."
```

Basta adicionar novas linhas seguindo esse padrão para criar novas regras,
ou editar o texto das respostas existentes.

## Personalizações já incluídas

- **Nome do app:** Balada CP (`res/values/strings.xml`)
- **Ícone:** bandeira da França, três faixas verticais (`res/drawable/ic_launcher_foreground.xml`)
- **Tela cheia:** ativada via `enableEdgeToEdge()` + tema `Theme.BaladaCP.Fullscreen`
- **Modo claro forçado:** `android:forceDarkAllowed="false"` no tema, e cores fixas no `MaterialTheme`
