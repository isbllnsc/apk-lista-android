#!/usr/bin/env python3
"""
Converte as arvores REAIS do Instagram lidas no celular do dono (rodada 1,
INSTAGRAM-APP-REAL.md) em fixtures de teste ANONIMIZADAS em
app/src/test/resources/real/. Os testes golden (com.listalocal.real) rodam o
codigo do app sobre elas.

    DONO_CONTAS=<conta-aberta>,<outra-conta> python app/src/test/tools/anonimizar_arvores.py <pasta-dos-dumps> [pasta-de-saida]

Entrada (fica FORA do git, tem terceiros):
  - <nome>.xml    : `uiautomator dump` (atividade principal: perfil, Direct, seguidores, Configuracoes);
  - <nome>_v.txt  : `cmd window dump-visible-window-views` + vdec.py (ModalActivity: conversa,
                    Nova mensagem, Amigos Proximos). Sem texto nem content-desc.

Saida: um XML por tela, no formato do uiautomator, so com os atributos que o app
le (id, classe, texto, content-desc, clicavel, marcado, selecionado, rolavel,
habilitado, bounds). Atributo com valor padrao e omitido.

PRIVACIDADE (regra: nada de terceiros no git):
  - Todo texto/content-desc passa por [anonimizar]: fica igual so o que e texto de
    UI do Instagram (lista fechada + moldes com numero); nome/@ de pessoa vira
    "pessoaNN" (quando tem a forma de um @, como o original) ou "Pessoa NN"
    (quando nao tem: espaco, acento, emoji); conteudo de mensagem/nota vira
    "mensagem NN". O mesmo original vira sempre o mesmo marcador em TODAS as telas
    (a linha da caixa de entrada e o cabecalho continuam batendo entre si).
  - O padrao e ANONIMIZAR: texto que nao esta na lista de UI nunca passa.
  - A conta do dono vira "minhaconta" (a outra dele, "outraconta").
  - Nenhum mapa original -> marcador e gravado.
Estrutura, ids, classes, bounds e estados ficam como na tela real.

Telas de Views (_v.txt): a classe vira a classe de acessibilidade provavel
(IgTextView -> android.widget.TextView...), a original fica em `view-class`; lista
(RecyclerView/ListView/ScrollView) e marcada rolavel (SUPOSTO: o dump de Views
nao diz). Sem texto no dump: so entram textos de UI DOCUMENTADOS por id
(INSTAGRAM-APP-REAL.md 1.b, 1.f, 3) e marcadores "amigoNN"/"Amigo NN" nos campos
de pessoa, todos com `suposto="1"`.
"""
import os
import re
import sys
import xml.etree.ElementTree as ET

LARGURA, ALTURA = 1080, 2408
IG = "com.instagram.android"

# (fixture, origem, o que e). Copias identicas (nova.xml, s00.xml = inbox.xml; t10f = _fx; _p = t11a) ficam de fora.
FIXTURES = [
    # atividade principal (uiautomator)
    ("perfil_proprio", "t09a.xml", "aba Perfil da conta aberta, topo (numeros de seguidores)"),
    ("perfil_proprio_2", "t11a.xml", "aba Perfil da conta aberta, outra leitura"),
    ("perfil_rolado", "home.xml", "aba Perfil rolada (numeros fora da tela)"),
    ("perfil_menu_criar", "t15a.xml", "aba Perfil com a folha 'Criar' aberta por cima"),
    ("caixa_principal", "t04.xml", "Direct, caixa de entrada, filtro Principal, topo"),
    ("caixa_principal_2", "inbox.xml", "Direct, caixa de entrada, outra leitura"),
    ("caixa_principal_3", "t16_final.xml", "Direct, caixa de entrada, fim da rodada"),
    ("caixa_rolada", "_ib.xml", "Direct, caixa de entrada rolada (filtros no topo)"),
    ("caixa_rolada_2", "_in1.xml", "Direct, caixa de entrada rolada, outra leitura"),
    ("caixa_velha_perfil_na_frente", "t07d.xml", "arvore VELHA da caixa de entrada com o perfil aberto por link na frente"),
    ("caixa_pedidos", "t15c.xml", "Direct, filtro Pedidos marcado (vazio)"),
    ("seguidores_topo", "t10a.xml", "lista de Seguidores, aba seguidores marcada, topo"),
] + [
    (f"seguidores_rolagem_{i:02d}", f"_f{i}.xml", f"lista de Seguidores rolada, passo {i} (arraste lento)")
    for i in range(13)
] + [
    ("seguidores_fim", "_fs.xml", "lista de Seguidores no fim: ultimas linhas e 'Sugestoes para voce'"),
    ("seguidores_so_sugestoes", "_fx.xml", "lista de Seguidores depois do fim: so Sugestoes"),
    ("seguidores_busca_1", "t10b.xml", "lista de Seguidores com busca digitada (1 linha)"),
    ("seguidores_busca_3", "t10e.xml", "lista de Seguidores com busca digitada (3 linhas)"),
    ("seguindo_topo", "t13a.xml", "mesma tela, aba SEGUINDO marcada"),
    ("sinalizadas", "t10c.xml", "mesma tela, aba Sinalizadas marcada (sem linhas)"),
    ("config_1", "t01a.xml", "Configuracoes e atividade (Bloks), topo"),
    ("config_2", "t01b.xml", "Configuracoes e atividade, arrastada 1x"),
    ("config_3", "t01c.xml", "Configuracoes e atividade, arrastada 2x"),
    ("config_4", "t01d.xml", "Configuracoes e atividade, 'Quem pode ver seu conteudo' com Amigos Proximos"),
    # ModalActivity (dump de Views, sem texto)
    ("v_amigos_topo", "t02a_v.txt", "Amigos Proximos aberta: cabecalho com 'Limpar tudo', linhas, Concluir"),
    ("v_amigos_membros_fim", "_cu_v.txt", "Amigos Proximos: ultimos membros marcados, cabecalho Sugestoes, sugestoes"),
    ("v_amigos_busca_membro", "t03b_v.txt", "Amigos Proximos: busca pelo @ de um membro (1 linha marcada)"),
    ("v_nova_mensagem", "t05b_v.txt", "Nova mensagem vazia ('Para:', busca, sugestoes)"),
    ("v_nova_mensagem_1_pessoa", "t05e_v.txt", "Nova mensagem com 1 pilula: conversa 1:1 embutida com campo"),
    ("v_nova_mensagem_grupo", "t05g_v.txt", "Nova mensagem com 2 pilulas: PREVIA DE GRUPO com campo"),
    ("v_conversa", "t07a_v.txt", "conversa 1:1 aberta pelo ig.me, campo vazio"),
    ("v_conversa_com_texto", "t06e_v.txt", "conversa com 1 letra no campo: Enviar aparece"),
    ("v_conversa_nova", "t07h_v.txt", "conversa nova (sem historico): Enviar visivel e DESABILITADO"),
    ("v_conversa_temporaria", "t10h_v.txt", "conversa com mensagens temporarias ligadas"),
    # rodada 1, telas fora das operacoes (o Instagram pode estar nelas quando o dono toca em Conferir/Iniciar)
    ("v_amigos_rolada", "_cf_v.txt", "Amigos Proximos rolada no meio da lista (sem cabecalho nem Limpar tudo)"),
    ("v_post", "t12b_v.txt", "post do perfil aberto pela grade, sem a barra de abas"),
    ("v_compartilhar", "t12a_v.txt", "folha de compartilhar aberta por cima do post (grade de pessoas)"),
    ("v_compartilhar_busca", "t12d_v.txt", "folha de compartilhar com busca digitada: linha com caixa recipient_toggle"),
    ("v_compartilhar_1", "t12e_v.txt", "folha de compartilhar com 1 pessoa marcada: campo e Enviar"),
    ("v_compartilhar_grupo", "t12g_v.txt", "folha com 2 marcadas: Enviar separadamente e Enviar para nova conversa em grupo"),
]

# ---------------------------------------------------------------- anonimizacao

# As contas do dono (nunca no codigo): DONO_CONTAS="<@ da conta aberta>,<@ da outra conta dele>".
DONO = dict(zip(
    (c.strip().lstrip("@").lower() for c in os.environ.get("DONO_CONTAS", "").split(",") if c.strip()),
    ("minhaconta", "outraconta"),
))

# Texto de UI do Instagram 448 pt-BR visto nas telas (fica igual).
UI = {
    "Acessibilidade", "Adicionar ao story", "Adicionar banners", "Adicionar nota", "Amigos Próximos",
    "Anúncio", "Arquivar e baixar", "Atividade no feed Amigos", "Bloqueados",
    "Cabeçalho de Classificado por Padrão",
    "Caso perfis com probabilidade de serem spam ou irrelevantes tentem seguir seu perfil, você poderá "
    "excluir ou confirmar os pedidos aqui. Saiba mais.",
    "Central de Contas",
    "Central de Contas, Senha, segurança, dados pessoais, experiências conectadas, preferências de anúncios",
    "Central de amizade", "Classificado por Padrão", "Classificar por", "Classificar por Mais recentes",
    "Comentários", "Como outras pessoas podem interagir com você", "Como você usa o Instagram",
    "Compartilhamento e reutilização", "Compartilhar perfil", "Conectar", "Conectar contatos",
    "Conectar contatos, Encontre pessoas conhecidas", "Configurações e atividade", "Contas silenciadas",
    "Criar", "Criar anúncio de mensagens", "Criar destaque", "Criar novo", "Criar novo post",
    "Criar novo reel", "Criar novo story", "Criar novo vídeo ao vivo", "Curtiu uma mensagem ·",
    "Destaques", "Editar perfil", "Edits", "Encontre pessoas conhecidas", "Enviado", "Enviou uma foto ·",
    "Favoritos", "Filtros", "Foto do perfil", "Fotos com você", "Geral", "Gerenciamento de tempo",
    "Gerenciar configurações", "Idioma e som", "Ignorar", "Instagram Plus", "Instagram Plus, Não assinante",
    "Instagram para tablets", "Itens Arquivados", "Limitar interações", "Limitar interações, Desativado",
    "Limpar texto", "Live", "Marcações e menções", "Mensagem", "Mensagens e respostas a stories",
    "Meta One", "Meta One, Não assinante", "NOVO", "Nenhum pedido de contato ainda",
    "Nenhum pedido sinalizado", "Notificações", "Notificações não lidas", "Nova mensagem", "Novo",
    "Número de curtidas e compartilhamentos", "O que você vê", "Ocultar story e live", "Online agora",
    "Opções", "Pagamentos de anúncios", "Painel profissional", "Palavras ocultas", "Pedido feito",
    "Pedidos", "Pedidos ocultos", "Perfil", "Permissões do dispositivo", "Pesquisar",
    "Pesquisar e explorar", "Ponto de entrada do painel profissional", "Post", "Posts cruzados",
    "Preferências de conteúdo", "Privacidade da conta", "Privacidade da conta, Público", "Página inicial",
    "Quem pode ver seu conteúdo", "Reagiu ao seu story: 😂 ·", "Reel", "Reels", "Repostados",
    "Respondeu ao seu story ·", "Restritas", "Salvos", "Seguir", "Seguir de volta",
    "Seguir e convidar amigos", "Selecionar várias mensagens",
    "Senha, segurança, dados pessoais, experiências conectadas, preferências de anúncios",
    "Seu app e suas mídias", "Seu perfil. Story não visto", "Seus insights e ferramentas", "Sinalizadas",
    "Story", "Sua atividade", "Sua conta", "Sua nota", "Sugestões para você", "Tipo e ferramentas da conta",
    "Toque para conversar ·", "Uso de dados e qualidade da mídia", "Usuário presente",
    "Ver perfil no Threads", "Ver todas as sugestões", "Visto", "Visto ontem", "Visualização em grade",
    "Você pode controlar quem pode enviar pedidos de contato para você nas configurações.", "Voltar",
    "posts", "seguidores", "seguindo", "Áudio original", "não lidas",
    # frases-sugestao do proprio Instagram na "Sua nota" (nao sao de ninguem)
    "A vibe do dia…", "Deixe este espaço com sua cara…", "Minha obsessão é…", "Não consigo decidir…",
    "Não vejo a hora de…", "Opinião impopular…", "Primeira nota da semana…",
}

UI_MOLDES = [re.compile(p) for p in (
    r"^\d+$",
    r"^\d+ (seguidores|seguindo|assinaturas|em comum|sem|d|h|min|s|pessoas)$",
    r"^\d+(seguidores|seguindo|posts)$",
    r"^\d+ novos? posts?$",
    r"^\d+ novas? mensage(ns|m) ·$",
    r"^Online há \d+ (h|min|d|sem)$",
    r"^[\d,]+\xa0?(mil )?visualizações nos últimos \d+ dias\.$",
    r"^@\d{6,}$",                 # referencia de recurso ("@2131976614"): nao e pessoa
    r"^#[0-9a-f]{8}$",            # cor
    r"^c?Principal \d+$",
    r"^(Amigos Próximos|Bloqueados|Contas silenciadas|Favoritos|Restritas), \d+$",
)]

# Ids cujo texto (fora da UI) e conteudo de mensagem/nota, nao nome.
IDS_CONTEUDO = {"pog_bubble_text", "pog_music_note_text"}

ARROBA = re.compile(r"^@?[A-Za-z0-9._]{1,30}$")


def ui(s):
    return s in UI or any(m.match(s) for m in UI_MOLDES)


class Anon:
    def __init__(self):
        self.mapa = {}
        self.n_pessoa = 0
        self.n_msg = 0

    @staticmethod
    def _borda(s, f):
        """Aplica f ao miolo, mantendo espacos das pontas e o '...' de truncado."""
        m = re.match(r"^(\s*)(.*?)(\s*)$", s, re.S)
        ini, miolo, fim = m.group(1), m.group(2), m.group(3)
        if not miolo:
            return s
        suf = ""
        for t in ("...", "…"):
            if miolo.endswith(t) and len(miolo) > len(t):
                miolo, suf = miolo[: -len(t)], t
                break
        return ini + f(miolo) + suf + fim

    def pessoa(self, s):
        def f(x):
            if x.lstrip("@") in DONO:
                return ("@" if x.startswith("@") else "") + DONO[x.lstrip("@")]
            k = ("p", x)
            if k not in self.mapa:
                self.n_pessoa += 1
                self.mapa[k] = f"pessoa{self.n_pessoa:02d}" if ARROBA.match(x) else f"Pessoa {self.n_pessoa:02d}"
            return self.mapa[k]
        return self._borda(s, f)

    def mensagem(self, s):
        def f(x):
            ponto = x.endswith(" ·")
            nucleo = x[:-2] if ponto else x
            k = ("m", nucleo)
            if k not in self.mapa:
                self.n_msg += 1
                self.mapa[k] = f"mensagem {self.n_msg:02d}"
            return self.mapa[k] + (" ·" if ponto else "")
        return self._borda(s, f)

    def texto(self, s, rid=""):
        """Um texto ou content-desc. Padrao: anonimizar."""
        if not s or ui(s):
            return s
        P, M = self.pessoa, self.mensagem
        moldes = [
            (r"^Enviar mensagem para (.+)$", lambda m: "Enviar mensagem para " + P(m[1])),
            (r"^Pediu para seguir (.+)$", lambda m: "Pediu para seguir " + P(m[1])),
            (r"^Seguir (.+)$", lambda m: "Seguir " + P(m[1])),
            (r"^Abrir story de (.+)$", lambda m: "Abrir story de " + P(m[1])),
            (r"^Mais opções para (.+)$", lambda m: "Mais opções para " + P(m[1])),
            (r"^(.+) profile picture$", lambda m: P(m[1]) + " profile picture"),
            (r"^Story de (.+?), (\d+ de \d+, Visualizado\.)$", lambda m: f"Story de {P(m[1])}, {m[2]}"),
            (r"^Story do destaque de (.+) na coluna (\d+)$", lambda m: f"Story do destaque de {P(m[1])} na coluna {m[2]}"),
            (r"^Reel de (.+) na linha (\d+), coluna (\d+)$", lambda m: f"Reel de {P(m[1])} na linha {m[2]}, coluna {m[3]}"),
            (r"^(.+) compartilhou uma nota que diz (.+)$", lambda m: f"{P(m[1])} compartilhou uma nota que diz {M(m[2])}"),
            (r"^(.+) está online agora e $", lambda m: f"{P(m[1])} está online agora e "),
        ]
        for pad, f in moldes:
            m = re.match(pad, s, re.S)
            if m:
                return f(m)
        if rid in IDS_CONTEUDO:
            return M(s)
        # "<nome>, <estado>, <tempo>" (linha da caixa de entrada): o nome e pessoa; o resto
        # fica se for UI, senao e previa de mensagem de alguem.
        if ", " in s and "\n" not in s:
            partes = s.split(", ")
            return ", ".join([P(partes[0])] + [p if ui(p) else M(p) for p in partes[1:]])
        if s.endswith(" ·"):
            return M(s)
        return P(s)


# ---------------------------------------------------------------- saida

ATRIBUTOS = [  # (nome no XML de saida, padrao omitido)
    ("text", ""), ("resource-id", ""), ("class", ""), ("content-desc", ""),
    ("clickable", "false"), ("long-clickable", "false"), ("checked", "false"), ("selected", "false"),
    ("scrollable", "false"), ("enabled", "true"), ("focused", "false"), ("bounds", ""),
]


def no_saida(pai, attrs):
    e = ET.SubElement(pai, "node")
    for k, padrao in ATRIBUTOS:
        v = attrs.get(k, padrao)
        if v != padrao:
            e.set(k, v)
    for k in ("view-class", "suposto"):
        if k in attrs:
            e.set(k, attrs[k])
    return e


def ler(caminho):
    # Caminho longo no Windows (> 260): prefixo \\?\ .
    p = os.path.abspath(caminho)
    if os.name == "nt" and not p.startswith(os.sep * 2):
        p = os.sep * 2 + "?" + os.sep + p
    with open(p, "rb") as f:
        return f.read()


def converter_uiautomator(dados, anon, raiz_saida):
    raiz = ET.fromstring(dados)

    def rec(n, pai):
        a = dict(n.attrib)
        rid = a.get("resource-id", "").split(":id/")[-1]
        for k in ("text", "content-desc"):
            if a.get(k):
                a[k] = anon.texto(a[k], rid)
        e = no_saida(pai, a)
        for c in n:
            rec(c, e)

    for n in raiz:
        rec(n, raiz_saida)


LINHA_V = re.compile(r"^(\s*)(\S+)((?: \S+)*?) \[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]$")
IDS_ANDROID = {"content", "list", "statusBarBackground", "navigationBarBackground"}

# Texto de UI documentado por id (INSTAGRAM-APP-REAL.md 1.b, 1.f, 3): o dump de Views nao tem texto.
TEXTO_DOC = {
    "direct_new_chat_to_field": "Para:",
    "row_header_action": "Limpar tudo",
    "done_button": "Concluir",
    "search_exit_button": "Cancelar",
    "audience_picker_disclaimer_text":
        "Não enviamos notificações quando você edita sua lista Amigos Próximos. Como funciona.",
    "row_thread_composer_edittext": "Mensagem...",
}
# Busca vazia devolve a dica como texto (como row_search_edit_text no uiautomator).
TEXTO_DOC_TELA = {"t02a_v.txt": {"search_edit_text": "Pesquisar"}, "_cu_v.txt": {"search_edit_text": "Pesquisar"},
                  "_cf_v.txt": {"search_edit_text": "Pesquisar"}, "t12a_v.txt": {"search_edit_text": "Pesquisar"},
                  # folha de compartilhar (INSTAGRAM-APP-REAL.md, compartilhar: t12e, t12g)
                  "t12e_v.txt": {"search_edit_text": "Pesquisar", "direct_private_share_message_box": "Escreva uma mensagem...",
                                 "direct_send_button_multi_select": "Enviar"},
                  "t12g_v.txt": {"search_edit_text": "Pesquisar", "direct_private_share_message_box": "Escreva uma mensagem...",
                                 "direct_send_button_multi_select": "Enviar separadamente",
                                 "direct_send_to_group_button_vertical": "Enviar para nova conversa em grupo (2)"}}
# Onde o texto do campo e desconhecido (letra digitada; dica temporaria nao lida).
SEM_TEXTO_DOC = {"t06e_v.txt": {"row_thread_composer_edittext"}, "t10h_v.txt": {"row_thread_composer_edittext"}}


def classe_a11y(v):
    if "EditText" in v or v.endswith("AutoCompleteTextView"):
        return "android.widget.EditText"
    if v.endswith("CheckBox"):
        return "android.widget.CheckBox"
    if v.endswith("RecyclerView"):
        return "androidx.recyclerview.widget.RecyclerView"
    if v.endswith("HorizontalScrollView"):
        return "android.widget.HorizontalScrollView"
    if v.endswith("ScrollView"):
        return "android.widget.ScrollView"
    if v.endswith("ListView"):
        return "android.widget.ListView"
    if "Button" in v:
        return "android.widget.Button"
    if v.endswith("TextView"):
        return "android.widget.TextView"
    if v.endswith("ImageView"):
        return "android.widget.ImageView"
    if v.endswith("LinearLayout"):
        return "android.widget.LinearLayout"
    if v.endswith("FrameLayout") or v == "DecorView":
        return "android.widget.FrameLayout"
    return "android.view.ViewGroup"


def converter_views(dados, origem, raiz_saida):
    linhas = dados.decode("utf-8").splitlines()
    doc = dict(TEXTO_DOC, **TEXTO_DOC_TELA.get(origem, {}))
    for k in SEM_TEXTO_DOC.get(origem, ()):
        doc.pop(k, None)
    pilha = [(-1, raiz_saida, True)]  # (profundidade, elemento, visivel)
    n_amigo = [0]
    for ln in linhas:
        if ln.startswith("==="):
            raiz_saida.set("janela", ln.split()[-1])
            continue
        m = LINHA_V.match(ln)
        if not m:
            continue
        prof = len(m.group(1)) // 2
        cls, marcas = m.group(2), m.group(3).split()
        l, t, r, b = (int(m.group(i)) for i in range(4, 8))
        rid = next((x[3:] for x in marcas if x.startswith("id/")), "")
        flags = {x for x in marcas if not x.startswith("id/")}
        while pilha[-1][0] >= prof:
            pilha.pop()
        pai, pai_visivel = pilha[-1][1], pilha[-1][2]
        l, t, r, b = max(l, 0), max(t, 0), min(r, LARGURA), min(b, ALTURA)
        visivel = pai_visivel and r > l and b > t  # sem area ou fora da tela: invisivel (o uiautomator nao traz)
        if not visivel:
            pilha.append((prof, pai, False))
            continue
        a = {
            "class": classe_a11y(cls), "view-class": cls, "bounds": f"[{l},{t}][{r},{b}]",
            "clickable": "true" if "C" in flags else "false",
            "checked": "true" if "K" in flags else "false",
            "selected": "true" if "S" in flags else "false",
            "enabled": "false" if "X" in flags else "true",
            "focused": "true" if "F" in flags else "false",
        }
        if a["class"].endswith(("RecyclerView", "ScrollView", "ListView")):
            a["scrollable"] = "true"  # SUPOSTO
        if rid and not rid.startswith("0x"):  # 0x18...: id gerado, sem nome
            a["resource-id"] = f"{'android' if rid in IDS_ANDROID else IG}:id/{rid}"
        if rid in doc:
            a["text"], a["suposto"] = doc[rid], "1"
        elif rid in ("row_user_primary_name", "grid_view_pog_text_view_first_line"):  # folha: nome antes do @
            n_amigo[0] += 1
            a["text"], a["suposto"] = f"Amigo {n_amigo[0]:02d}", "1"
        elif rid == "row_user_secondary_name":
            a["text"], a["suposto"] = f"amigo{n_amigo[0]:02d}", "1"
        elif rid in ("row_user_username", "row_user_info"):
            if rid == "row_user_username":
                n_amigo[0] += 1
            a["text"] = f"amigo{n_amigo[0]:02d}" if rid == "row_user_username" else f"Amigo {n_amigo[0]:02d}"
            a["suposto"] = "1"
        e = no_saida(pai, a)
        pilha.append((prof, e, True))
    if origem == "t03b_v.txt":
        # Busca pelo @ exato de um membro: o campo tem o @ da unica linha (doc 3).
        campo = next(x for x in raiz_saida.iter("node") if x.get("resource-id", "").endswith(":id/search_edit_text"))
        linha = next(x for x in raiz_saida.iter("node") if x.get("resource-id", "").endswith(":id/row_user_username"))
        campo.set("text", linha.get("text"))
        campo.set("suposto", "1")


def main():
    if len(sys.argv) < 2 or not DONO:
        sys.exit(__doc__)
    entrada = sys.argv[1]
    aqui = os.path.dirname(os.path.abspath(__file__))
    saida = sys.argv[2] if len(sys.argv) > 2 else os.path.join(aqui, "..", "resources", "real")
    os.makedirs(saida, exist_ok=True)
    anon = Anon()
    # Ordem fixa: os marcadores (pessoa01...) saem sempre iguais para os mesmos dumps.
    for nome, origem, oque in FIXTURES:
        dados = ler(os.path.join(entrada, origem))
        views = origem.endswith("_v.txt")
        raiz = ET.Element("hierarchy", {
            "fonte": "views" if views else "uiautomator",
            "origem": origem, "tela": oque, "largura": str(LARGURA), "altura": str(ALTURA),
        })
        if views:
            converter_views(dados, origem, raiz)
        else:
            converter_uiautomator(dados, anon, raiz)
        ET.indent(raiz, space=" ")
        with open(os.path.join(saida, nome + ".xml"), "wb") as f:
            f.write(b"<?xml version='1.0' encoding='UTF-8'?>\n")
            f.write(b"<!-- Arvore REAL anonimizada (app/src/test/tools/anonimizar_arvores.py). Nomes/@ = marcadores. -->\n")
            f.write(ET.tostring(raiz, encoding="utf-8"))
    print(f"{len(FIXTURES)} telas em {os.path.normpath(saida)}; {anon.n_pessoa} pessoas e {anon.n_msg} mensagens anonimizadas")


if __name__ == "__main__":
    main()
