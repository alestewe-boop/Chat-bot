@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.baladacp.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

// ---------- Cores da bandeira da França ----------
val FranceBlue = Color(0xFF0055A4)
val FranceWhite = Color(0xFFFFFFFF)
val FranceRed = Color(0xFFEF4135)
val BubbleBot = Color(0xFFF1F1F1)
val TextDark = Color(0xFF1A1A1A)

data class ChatMessage(val text: String, val isUser: Boolean)

/**
 * Guarda a chave da API da IA no aparelho (não vai pro GitHub, fica só
 * salva localmente via SharedPreferences).
 */
object Config {
    private const val PREFS = "balada_cp_prefs"
    private const val KEY_API = "gemini_api_key"

    fun getApiKey(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY_API, "") ?: ""
    }

    fun setApiKey(context: Context, key: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_API, key.trim()).apply()
    }
}

/**
 * Motor de respostas por regras fixas (usado como reserva quando não há
 * chave de IA configurada ou quando a chamada à internet falha).
 * Adicione/edite pares palavra-chave -> resposta aqui.
 */
object RuleEngine {
    private val regras = linkedMapOf(
        listOf("oi", "olá", "ola", "eae", "e ai") to
            "Olá! Bem-vindo(a) ao Balada CP 🇫🇷 Como posso ajudar?",
        listOf("horário", "horario", "que horas", "abre", "fecha") to
            "Abrimos às 22h e fechamos às 5h. Te esperamos na Balada CP!",
        listOf("ingresso", "entrada", "preço", "preco", "quanto custa") to
            "Os ingressos custam a partir de R$ 30 na lista e podem ser comprados no site oficial.",
        listOf("endereço", "endereco", "onde fica", "localização", "localizacao") to
            "Estamos localizados no centro da cidade — te mando o mapa em breve!",
        listOf("evento", "atração", "atracao", "dj", "banda") to
            "Toda semana temos um DJ ou atração especial. Fica de olho nas nossas redes!",
        listOf("obrigado", "obrigada", "valeu", "vlw") to
            "Por nada! Nos vemos na pista 🎉",
        listOf("tchau", "até mais", "ate mais", "flw") to
            "Até mais! Bora pra Balada CP! 🇫🇷"
    )

    private val padrao = listOf(
        "Não entendi bem, pode reformular?",
        "Ainda estou aprendendo! Pergunte sobre horários, ingressos ou eventos.",
        "Boa pergunta! Tente perguntar sobre a Balada CP de outro jeito."
    )

    fun responder(mensagem: String): String {
        val texto = mensagem.trim().lowercase()
        for ((palavras, resposta) in regras) {
            if (palavras.any { texto.contains(it) }) {
                return resposta
            }
        }
        return padrao.random()
    }
}

/**
 * Motor de IA: manda a pergunta (com o histórico da conversa) pra API do
 * Gemini (Google) e devolve a resposta em texto.
 *
 * Pra funcionar, o usuário precisa colar uma chave de API gratuita gerada em
 * https://aistudio.google.com/apikey nas Configurações do app (ícone ⚙).
 */
object AiEngine {

    // Contexto fixo sobre a Balada CP para a IA responder certo sobre o local.
    private const val CONTEXTO_LOCAL = """
        Você é o assistente virtual do "Balada CP", uma casa noturna.
        Responda sempre em português do Brasil, de forma curta, simpática e direta
        (no máximo 3-4 frases), usando emojis com moderação.
        Informações fixas sobre a casa (use quando perguntarem sobre o local):
        - Horário: abre às 22h e fecha às 5h.
        - Ingressos: a partir de R$ 30 na lista, vendidos no site oficial.
        - Localização: centro da cidade.
        - Toda semana tem DJ ou atração especial, anunciada nas redes sociais.
        Se perguntarem qualquer outra coisa fora desses temas (curiosidades gerais,
        piadas, etc.), responda normalmente usando seu conhecimento.
        Se não tiver certeza sobre algo específico da casa (ex: line-up de um dia exato),
        diga que a informação será confirmada nas redes sociais oficiais, sem inventar.
    """

    private const val MODEL = "gemini-2.5-flash"
    private const val ENDPOINT_BASE =
        "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent?key="

    /**
     * Faz a chamada de rede (bloqueante) na thread de IO e devolve o texto
     * de resposta. Lança exceção se algo der errado (sem internet, chave
     * inválida, etc.) — quem chama deve tratar com try/catch.
     */
    suspend fun perguntar(apiKey: String, historico: List<ChatMessage>): String =
        withContext(Dispatchers.IO) {
            val url = URL(ENDPOINT_BASE + apiKey)
            val conexao = url.openConnection() as HttpURLConnection
            conexao.requestMethod = "POST"
            conexao.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conexao.doOutput = true
            conexao.connectTimeout = 15000
            conexao.readTimeout = 20000

            val corpo = JSONObject().apply {
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().put("text", CONTEXTO_LOCAL)))
                })
                put("contents", montarConteudo(historico))
            }

            conexao.outputStream.use { saida ->
                OutputStreamWriter(saida, StandardCharsets.UTF_8).use { escritor ->
                    escritor.write(corpo.toString())
                }
            }

            val status = conexao.responseCode
            val stream = if (status in 200..299) conexao.inputStream else conexao.errorStream
            val resposta = BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8))
                .use { it.readText() }

            if (status !in 200..299) {
                throw RuntimeException("Erro da API (código $status): $resposta")
            }

            val json = JSONObject(resposta)
            val candidatos = json.optJSONArray("candidates")
            if (candidatos == null || candidatos.length() == 0) {
                throw RuntimeException("A IA não retornou nenhuma resposta.")
            }
            val partes = candidatos.getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")

            val texto = StringBuilder()
            for (i in 0 until partes.length()) {
                texto.append(partes.getJSONObject(i).optString("text", ""))
            }
            texto.toString().trim().ifBlank {
                throw RuntimeException("A IA retornou uma resposta vazia.")
            }
        }

    /** Converte o histórico de mensagens no formato exigido pela API do Gemini. */
    private fun montarConteudo(historico: List<ChatMessage>): JSONArray {
        val array = JSONArray()
        // A API do Gemini exige que a conversa comece com uma mensagem do
        // usuário (role "user"). Como a primeira mensagem da lista é sempre
        // a saudação do bot, descartamos tudo antes da primeira msg do usuário.
        val primeiraDoUsuario = historico.indexOfFirst { it.isUser }
        val semSaudacaoInicial =
            if (primeiraDoUsuario >= 0) historico.subList(primeiraDoUsuario, historico.size) else historico
        // Limita ao histórico recente para manter a chamada rápida e barata.
        val recentes = semSaudacaoInicial.takeLast(20)
        for (msg in recentes) {
            val item = JSONObject().apply {
                put("role", if (msg.isUser) "user" else "model")
                put("parts", JSONArray().put(JSONObject().put("text", msg.text)))
            }
            array.put(item)
        }
        return array
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Tela cheia (edge-to-edge) + força modo claro independente do sistema
        enableEdgeToEdge()

        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = FranceBlue,
                    secondary = FranceRed,
                    background = FranceWhite,
                    surface = FranceWhite
                )
            ) {
                BaladaCPScreen()
            }
        }
    }
}

@Composable
fun BaladaCPScreen() {
    val context = LocalContext.current
    val mensagens = remember {
        mutableStateListOf(
            ChatMessage("Olá! Bem-vindo(a) ao Balada CP 🇫🇷 Pergunte sobre horários, ingressos ou eventos.", isUser = false)
        )
    }
    var textoAtual by remember { mutableStateOf("") }
    var carregando by remember { mutableStateOf(false) }
    var mostrarConfig by remember { mutableStateOf(false) }
    var apiKey by remember { mutableStateOf(Config.getApiKey(context)) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    fun enviarMensagem() {
        if (textoAtual.isBlank() || carregando) return
        val pergunta = textoAtual
        mensagens.add(ChatMessage(pergunta, isUser = true))
        textoAtual = ""

        if (apiKey.isBlank()) {
            // Sem chave configurada: usa o modo de palavras-chave, sem precisar de internet.
            mensagens.add(ChatMessage(RuleEngine.responder(pergunta), isUser = false))
            scope.launch { listState.animateScrollToItem(mensagens.size - 1) }
            return
        }

        carregando = true
        scope.launch {
            listState.animateScrollToItem(mensagens.size - 1)
            val resposta = try {
                AiEngine.perguntar(apiKey, mensagens.toList())
            } catch (e: Exception) {
                // Se a IA falhar (sem internet, chave inválida, etc.), cai no modo
                // antigo, mas avisa o motivo pra facilitar o diagnóstico.
                "⚠️ Não consegui falar com a IA agora (${e.message ?: "erro desconhecido"}).\n\n" +
                    RuleEngine.responder(pergunta)
            }
            mensagens.add(ChatMessage(resposta, isUser = false))
            carregando = false
            listState.animateScrollToItem(mensagens.size - 1)
        }
    }

    if (mostrarConfig) {
        ConfigDialog(
            apiKeyAtual = apiKey,
            onSalvar = { novaChave ->
                apiKey = novaChave
                Config.setApiKey(context, novaChave)
                mostrarConfig = false
            },
            onFechar = { mostrarConfig = false }
        )
    }

    Scaffold(
        containerColor = FranceWhite,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Bandeira da França como símbolo principal do app
                        BandeiraFranca(modifier = Modifier
                            .width(28.dp)
                            .height(20.dp)
                            .clip(RoundedCornerShape(2.dp)))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Balada CP", fontWeight = FontWeight.Bold, color = FranceWhite)
                    }
                },
                actions = {
                    TextButton(onClick = { mostrarConfig = true }) {
                        Text(if (apiKey.isBlank()) "⚙ Ativar IA" else "⚙", color = FranceWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = FranceBlue)
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(FranceWhite)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = textoAtual,
                    onValueChange = { textoAtual = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Digite sua mensagem...") },
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = FranceBlue,
                        unfocusedBorderColor = Color.LightGray
                    ),
                    singleLine = true,
                    enabled = !carregando
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { enviarMensagem() },
                    enabled = !carregando,
                    colors = ButtonDefaults.buttonColors(containerColor = FranceRed),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    if (carregando) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = FranceWhite,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Enviar", color = FranceWhite)
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(FranceWhite)
                .padding(innerPadding)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            items(mensagens) { msg ->
                BolhaMensagem(msg)
            }
            if (carregando) {
                item {
                    BolhaMensagem(ChatMessage("digitando...", isUser = false))
                }
            }
        }
    }
}

@Composable
fun ConfigDialog(apiKeyAtual: String, onSalvar: (String) -> Unit, onFechar: () -> Unit) {
    var texto by remember { mutableStateOf(apiKeyAtual) }
    AlertDialog(
        onDismissRequest = onFechar,
        title = { Text("Ativar respostas com IA") },
        text = {
            Column {
                Text(
                    "Cole aqui sua chave gratuita da API do Gemini. Gere a sua em " +
                        "aistudio.google.com/apikey (entre com uma conta Google).",
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = texto,
                    onValueChange = { texto = it },
                    placeholder = { Text("Cole a chave da API aqui") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Deixe em branco para usar só as respostas prontas por palavra-chave.",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSalvar(texto) }) { Text("Salvar") }
        },
        dismissButton = {
            TextButton(onClick = onFechar) { Text("Cancelar") }
        }
    )
}

@Composable
fun BolhaMensagem(msg: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .background(
                    color = if (msg.isUser) FranceBlue else BubbleBot,
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .widthIn(max = 280.dp)
        ) {
            Text(
                text = msg.text,
                color = if (msg.isUser) FranceWhite else TextDark,
                fontSize = 15.sp
            )
        }
    }
}

@Composable
fun BandeiraFranca(modifier: Modifier = Modifier) {
    Row(modifier = modifier) {
        Box(modifier = Modifier.weight(1f).fillMaxHeight().background(FranceBlue))
        Box(modifier = Modifier.weight(1f).fillMaxHeight().background(FranceWhite))
        Box(modifier = Modifier.weight(1f).fillMaxHeight().background(FranceRed))
    }
}
